const express = require('express');
const {HTTP} = require("cloudevents");
const jsonata = require('jsonata');
const fs = require('node:fs');
const fsPromises = require('node:fs').promises;
const {buffer} = require('node:stream/consumers');

const port = process.env.PORT = process.env.PORT || 8080;
const k_sink = process.env.K_SINK || undefined;
const jsonata_transform_file_name = process.env.JSONATA_TRANSFORM_FILE_NAME || undefined;

// Allow transforming the response received by the endpoint defined by K_SINK
const jsonata_response_transform_file_name = process.env.JSONATA_RESPONSE_TRANSFORM_FILE_NAME || undefined;

const jsonata_discard_response_body = process.env.JSONATA_DISCARD_RESPONSE_BODY === "true" || false;

const oidc_token_file = process.env.OIDC_TOKEN_FILE || undefined
if (oidc_token_file && !fs.existsSync(oidc_token_file)) {
    console.info(`${oidc_token_file} file doesn't exist, token will not be forwarded to K_SINK endpoint (if specified)`);
} else if (oidc_token_file) {
    console.info(`${oidc_token_file} file exist, token will be forwarded to K_SINK endpoint (if specified)`);
}

if (!jsonata_transform_file_name) {
    throw new Error("undefined JSONATA_TRANSFORM_FILE_NAME env variable");
}
if (!k_sink && jsonata_response_transform_file_name) {
    throw new Error("undefined K_SINK env variable with defined JSONATA_RESPONSE_TRANSFORM_FILE_NAME");
}

if (k_sink) {
    console.info("K_SINK is specified, transformations will be sent to that endpoint")
}

let jsonata_transform = null
let jsonata_response_transform = null

try {
    const jsonata_transform_file_content = fs.readFileSync(jsonata_transform_file_name, {encoding: 'utf-8'})
    jsonata_transform = jsonata(jsonata_transform_file_content);
} catch (error) {
    throw new Error(`Failed to parse Jsonata transform file in ${jsonata_transform_file_name}: ${error}`);
}

if (jsonata_response_transform_file_name) {
    try {
        const jsonata_response_transform_file_content = fs.readFileSync(jsonata_response_transform_file_name, {encoding: 'utf-8'});
        jsonata_response_transform = jsonata(jsonata_response_transform_file_content);
    } catch (error) {
        throw new Error(`Failed to parse Jsonata response transform file in ${jsonata_response_transform_file_name}: ${error}`);
    }
}

function logDebug(...inputs) {
    if (process.env.NODE_ENV === "development") {
        console.debug(...inputs);
    }
}

const app = express()
app.use(express.json());
app.use(express.text());
app.use(express.raw({type: '*/*'}));
app.use((req, res, next) => {
    if (Buffer.isBuffer(req.body)) {
        try {
            req.rawBody = JSON.parse(req.body);
        } catch (error) {
            logDebug("Falling back to convert body to string");
            req.rawBody = req.body.toString()
        }
    } else {
        req.rawBody = req.body;
    }
    next();
});

app.post("/", async (req, res) => {
    try {
        let input = null
        try {
            const ceInput = HTTP.toEvent({headers: req.headers, body: req.rawBody});
            if (Array.isArray(ceInput)) {
                logDebug("Unsupported batch ceInput")
                return res
                    .header("Reason", "Unsupported batch input")
                    .status(400)
                    .send();
            }
            input = JSON.parse(HTTP.structured(ceInput).body)
        } catch (error) {
            logDebug(`Failed to deserialize CloudEvent, falling back to raw body`, JSON.stringify(req.rawBody, null, 2), error)
            input = req.rawBody
        }

        logDebug("Input", JSON.stringify(input));

        const transformed = await jsonata_transform.evaluate(input)
        const transformed_content_type = guessTransformedContentType(transformed)

        logDebug("Transformed input", JSON.stringify(transformed, null, 2))

        if (!k_sink) {
            return res
                .header("Content-Type", transformed_content_type)
                .status(200)
                .send(JSON.stringify(transformed));
        }

        logDebug(`K_SINK is set, sending event to it ${k_sink}`)

        const k_sink_request_headers = {
            "Content-Type": transformed_content_type
        }
        if (oidc_token_file) {
            const token = await fsPromises.readFile(oidc_token_file, {encoding: 'utf-8'})
            if (token && token.length > 0) {
                k_sink_request_headers.Authorization = `Bearer ${token}`
            }
        }

        const response = await fetch(k_sink, {
            method: "POST",
            headers: k_sink_request_headers,
            body: JSON.stringify(transformed),
            redirect: 'error',
        })

        if (jsonata_discard_response_body) {
            logDebug(`Received response from K_SINK, discarding response body and responding with ${response.status}`)

            return res
                .status(response.status)
                .send()
        }

        if (!jsonata_response_transform_file_name) {
            logDebug(`Received response from K_SINK (status: ${response.status}), propagating response body as response`)

            const content_type = response.headers["Content-Type"]
            if (content_type && content_type.length > 0) {
                res.setHeader('Content-Type', content_type)
            }

            res.status(response.status)

            if (response.body) {
                return response.body.pipeTo(new WritableStream({
                    write(chunk) {
                        res.write(chunk)
                    },
                    close() {
                        res.end()
                    },
                }))
            }
            return res.send()
        }

        logDebug(`Received response from K_SINK ${response.status}, transforming response body with transformation in ${jsonata_response_transform_file_name}`)

        const response_buf = await buffer(response.body)

        try {
            const response_headers = {}
            response.headers.forEach((value, key) => {
                if (key in response_headers) {
                    response_headers[key].push(value)
                    return
                }
                response_headers[key] = [value]
            })
            const ce_input = HTTP.toEvent({headers: response_headers, body: response_buf});
            input = JSON.parse(HTTP.structured(ce_input).body)
        } catch (error) {
            const body = response_buf.toString('utf-8')
            try {
                input = JSON.parse(body)
            } catch (error) {
                input = body
            }
        }

        logDebug(`Transforming response body with transformation in ${jsonata_response_transform_file_name}, using input`, JSON.stringify(input, null, 2))

        const transformed_response = await jsonata_response_transform.evaluate(input)
        const transformed_response_content_type = guessTransformedContentType(transformed_response)

        return res
            .header("Content-Type", transformed_response_content_type)
            .status(response.status)
            .send(JSON.stringify(transformed_response))

    } catch (error) {
        console.error(error);
        return res
            .header("Reason", error.toString())
            .status(500)
            .send()
    }
});

// guessTransformedContentType tries to guess the transformed event content type.
// 1. If the transformed event contains a special "contentype" field, it returns it.
// 2. Otherwise, it tries to find CloudEvents "specversion" attribute and, if it's present, returns
// the CloudEvent structured content type "application/cloudevents+json".
// 3. Lastly, it falls back to "application/json" if none of the above are specified.
function guessTransformedContentType(transformed) {
    if ("contenttype" in transformed && transformed['contenttype']) {
        return transformed['contenttype'].toString();
    }
    if ("specversion" in transformed) {
        return "application/cloudevents+json"
    }
    return "application/json";
}

app.get('/healthz', (req, res) => {
    res.status(200).send('OK');
});

app.get('/readyz', (req, res) => {
    res.status(200).send('READY');
});

app.disable('x-powered-by');

const server = app.listen(port, () => {
    console.log(`Jsonata server listening on port ${port}`)
})

process.on('SIGINT', shutDown);
process.on('SIGTERM', shutDownNow);

let connections = [];

server.on('connection', connection => {
    connections.push(connection);
    connection.on('close', () => connections = connections.filter(curr => curr !== connection));
});

function shutDown() {
    console.log('Received interrupt signal, shutting down gracefully');

    if (connections.length === 0) {
        shutDownNow()
    } else {
        setTimeout(() => {
            shutDownNow()
        }, 5000);
    }
}

function shutDownNow() {
    console.log('Shutting down gracefully');

    server.close(() => {
        console.log('Closed out remaining connections');
        process.exit(0);
    });

    setTimeout(() => {
        console.error('Could not close connections in time, forcefully shutting down');
        process.exit(1);
    }, 10000);

    connections.forEach(curr => {
        if (curr) {
            curr.end()
        }
    });

    setTimeout(() => connections.forEach(curr => {
        if (curr) {
            curr.destroy()
        }
    }), 7000);
}
