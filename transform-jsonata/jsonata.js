const express = require('express');
const {HTTP} = require("cloudevents");
const jsonata = require('jsonata');
const fs = require('node:fs');

const port = process.env.PORT = process.env.PORT || 8080;
const k_sink = process.env.K_SINK || undefined;
const jsonata_transform_file_name = process.env.JSONATA_TRANSFORM_FILE_NAME || undefined;

if (!jsonata_transform_file_name) {
    throw new Error("undefined JSONATA_TRANSFORM_FILE_NAME env variable");
}

let jsonata_transform = null

try {
    const jsonata_transform_file_content = fs.readFileSync(jsonata_transform_file_name, "utf-8")
    jsonata_transform = jsonata(jsonata_transform_file_content);
} catch (error) {
    throw new Error(`Failed to parse Jsonata transform file in ${jsonata_transform_file_name}: ${error}`);
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
            logDebug(`Failed to deserialize CloudEvent, falling back to raw body`, JSON.stringify(req.rawBody), error)
            input = req.rawBody
        }

        logDebug("input", JSON.stringify(input));

        const transformed = await jsonata_transform.evaluate(input)
        if (k_sink) {
            logDebug(`K_SINK is set, sending event to it ${k_sink}`)

            try {
                const response = await fetch(k_sink, {
                    method: "POST",
                    headers: {
                        "Content-Type": "application/json",
                    },
                    body: JSON.stringify(transformed),
                })
                logDebug(`K_SINK received response ${response.status}`)

                return res
                    .status(response.status)
                    .send()
            } catch (error) {
                return res
                    .header("Reason", error.toString())
                    .status(502)
                    .send()
            }
        }

        logDebug("Transformed input", JSON.stringify(transformed, null, 2))

        return res
            .header("Content-Type", "application/json")
            .status(200)
            .send(transformed);

    } catch (error) {
        console.error(error);
        return res
            .header("Reason", error.toString())
            .status(500)
            .send()
    }
});

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
