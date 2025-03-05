const express = require('express');
const {HTTP} = require("cloudevents");
const jsonata = require('jsonata');
const fs = require('node:fs');
const http = require('http');
const https = require('https');
const fsPromises = require('node:fs').promises;
const {buffer} = require('node:stream/consumers');

const {
    trace,
    context,
    SpanStatusCode,
    propagation,
    DiagConsoleLogger,
    DiagLogLevel,
    diag
} = require('@opentelemetry/api');
const {NodeTracerProvider} = require('@opentelemetry/sdk-trace-node');
const {Resource} = require('@opentelemetry/resources');
const {
    ATTR_SERVICE_NAME,
    ATTR_SERVER_ADDRESS,
    ATTR_SERVER_PORT,
    ATTR_URL_SCHEME
} = require('@opentelemetry/semantic-conventions');
const {
    BatchSpanProcessor,
    ParentBasedSampler,
    TraceIdRatioBasedSampler, ConsoleSpanExporter
} = require('@opentelemetry/sdk-trace-base');
const {ZipkinExporter} = require('@opentelemetry/exporter-zipkin');
const {W3CTraceContextPropagator, CompositePropagator} = require("@opentelemetry/core");
const {B3InjectEncoding, B3Propagator} = require("@opentelemetry/propagator-b3");

const httpPort = process.env.HTTP_PORT || 8080;
const httpsPort = process.env.HTTPS_PORT || 8443;
const httpsCertPath = process.env.HTTPS_CERT_PATH;
const httpsKeyPath = process.env.HTTPS_KEY_PATH;
const disableHTTPServer = process.env.DISABLE_HTTP_SERVER === 'true';
const k_sink = process.env.K_SINK || undefined;
const jsonata_transform_file_name = process.env.JSONATA_TRANSFORM_FILE_NAME || undefined;

if (disableHTTPServer && (!httpsKeyPath || !fs.existsSync(httpsKeyPath) || !httpsCertPath || !fs.existsSync(httpsCertPath))) {
   throw new Error(`HTTP and HTTPS server are both disabled, disableHTTPServer='${disableHTTPServer}', httpsKeyPath='${httpsKeyPath}', httpsCertPath=${httpsCertPath}`);
}

// Allow transforming the response received by the endpoint defined by K_SINK
const jsonata_response_transform_file_name = process.env.JSONATA_RESPONSE_TRANSFORM_FILE_NAME || undefined;

const jsonata_discard_response_body = process.env.JSONATA_DISCARD_RESPONSE_BODY === "true" || false;

const jsonata_config_tracing = JSON.parse(process.env.K_TRACING_CONFIG || '{}')

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

const w3cPropagator = new W3CTraceContextPropagator();
const propagator = new CompositePropagator({
    propagators: [
        w3cPropagator,
        new B3Propagator({
            injectEncoding: B3InjectEncoding.MULTI_HEADER
        }),
        new B3Propagator({
            injectEncoding: B3InjectEncoding.SINGLE_HEADER,
        })
    ],
})

if (process.env.NODE_ENV === "development") {
    // Enable OpenTelemetry debug logging
    diag.setLogger(new DiagConsoleLogger(), DiagLogLevel.DEBUG);
}

logDebug(jsonata_config_tracing)

let exporter = undefined
if ("zipkin-endpoint" in jsonata_config_tracing &&
    jsonata_config_tracing['zipkin-endpoint'] !== "" &&
    'backend' in jsonata_config_tracing &&
    jsonata_config_tracing.backend === 'zipkin') {

    console.info("Using zipkin tracing exporter")
    exporter = new ZipkinExporter({
        url: jsonata_config_tracing['zipkin-endpoint'],
        serviceName: 'transform-jsonata',
    })
} else {
    console.info("Using console tracing exporter")
    exporter = new ConsoleSpanExporter()
}

const batchSpanProcessor = new BatchSpanProcessor(exporter, {
    maxQueueSize: 1000, // Maximum queue size. After this, spans are dropped
    maxExportBatchSize: 100, // Maximum batch size of every export
    scheduledDelayMillis: 5000, // Delay interval between two consecutive exports
    exportTimeoutMillis: 30000, // How long the export can run before it is cancelled
});

let sampleRate = undefined
if ('sample-rate' in jsonata_config_tracing) {
    sampleRate = Number.parseFloat(jsonata_config_tracing['sample-rate']);
    console.info(`Tracing sample rate is ${sampleRate}`)
}

const traceProvider = new NodeTracerProvider({
    resource: new Resource({
        [ATTR_SERVICE_NAME]: 'transform-jsonata',
    }),
    spanProcessors: [batchSpanProcessor],
    sampler: new ParentBasedSampler({
        root: new TraceIdRatioBasedSampler(sampleRate ?? 0),
    }),
});

traceProvider.register({propagator: propagator});

// Get a tracer
const tracer = trace.getTracer('tracer');

const app = express()
app.use((req, res, next) => {
    const parentContext = propagation.extract(context.active(), req.headers);

    const span = tracer.startSpan(
        'http_request',
        {
            attributes: {
                'http.method': req.method,
                'http.url': req.url,
                'http.route': req.route?.path,
            },
        },
        parentContext
    );

    // Store the span in the context
    return context.with(trace.setSpan(context.active(), span), () => {
        // Add a callback to end the span when the response is finished
        res.on('finish', () => {
            span.setAttributes({
                'http.status_code': res.statusCode,
            });
            span.end();
        });
        next();
    });
});
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

const headerSetter = {
    set: (carrier, key, value) => {
        carrier[key] = value;
    }
};

app.post("/", async (req, res) => {
    const processSpan = tracer.startSpan('process_request');
    const processSpanContext = trace.setSpan(context.active(), processSpan)

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

        const k_sink_url = new URL(k_sink)
        const kSinkSendSpan = tracer.startSpan('k_sink_send', {
            attributes: {
                [ATTR_URL_SCHEME]: k_sink_url.protocol.endsWith(':') ? k_sink_url.protocol.substring(0, k_sink_url.protocol.length - 1) : k_sink_url.protocol,
                [ATTR_SERVER_ADDRESS]: k_sink_url.hostname,
                [ATTR_SERVER_PORT]: k_sink_url.port,
            }
        }, processSpanContext);

        const response = await context.with(
            trace.setSpan(context.active(), kSinkSendSpan),
            async () => {
                try {
                    w3cPropagator.inject(context.active(), k_sink_request_headers, headerSetter)

                    const result = await fetch(k_sink, {
                        method: "POST",
                        headers: k_sink_request_headers,
                        body: JSON.stringify(transformed),
                        redirect: 'error',
                        signal: req.signal,
                    })
                    kSinkSendSpan.setAttributes({
                        'http.status_code': result.status,
                        'http.response_content_length': result.headers.get('content-length'),
                    });
                    return result;
                } catch (error) {
                    kSinkSendSpan.recordException(error);
                    kSinkSendSpan.setStatus({code: SpanStatusCode.ERROR});
                    throw error;
                } finally {
                    kSinkSendSpan.end();
                }
            }
        );

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
                // Technically headers can have multiple values, however the JS SDK doesn't handle that case.
                response_headers[key] = value
            })
            const ce_input = HTTP.toEvent({headers: response_headers, body: response_buf});
            input = JSON.parse(HTTP.structured(ce_input).body)
        } catch (error) {
            const body = response_buf.toString('utf-8')
            try {
                input = JSON.parse(body)
                logDebug(`Response is not a CloudEvent, falling back to raw JSON parsing`, JSON.stringify(input, null, 2), error)
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
        processSpan.recordException(error)
        processSpan.setStatus({code: SpanStatusCode.ERROR});
        console.error(error);
        return res
            .header("Reason", error.toString())
            .status(500)
            .send()
    } finally {
        processSpan.end();
    }
});

// guessTransformedContentType tries to guess the transformed event content type.
// 1. If the transformed event contains a special "contenttype" field, it returns it.
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

let httpServer = null
let httpsServer = null

if (!disableHTTPServer) {
    httpServer = http.createServer(app)
        .listen(httpPort, () => {
            console.log(`Jsonata HTTP server listening on port ${httpPort}`)
        })
}

if (httpsCertPath && httpsKeyPath) {
    const httpsServerOptions = {
        cert: fs.readFileSync(httpsCertPath),
        key: fs.readFileSync(httpsKeyPath),

        // TLS Versions
        minVersion: 'TLSv1.2', // Minimum TLS version (avoid older, less secure protocols)
        maxVersion: 'TLSv1.3', // Maximum TLS version

        // Cipher Suites
        ciphers: [
            'ECDHE-ECDSA-AES128-GCM-SHA256',
            'ECDHE-RSA-AES128-GCM-SHA256',
            'ECDHE-ECDSA-AES256-GCM-SHA384',
            'ECDHE-RSA-AES256-GCM-SHA384',
            'ECDHE-ECDSA-CHACHA20-POLY1305',
            'ECDHE-RSA-CHACHA20-POLY1305',
            'DHE-RSA-AES128-GCM-SHA256',
            'DHE-RSA-AES256-GCM-SHA384'
        ].join(':'),

        // Attempt to use server cipher suite preference instead of clients
        honorCipherOrder: true,

        // Additional security options
        secureOptions:
            require('constants').SSL_OP_NO_TLSv1 |
            require('constants').SSL_OP_NO_TLSv1_1 |
            require('constants').SSL_OP_NO_COMPRESSION,
    }

    httpsServer = https.createServer(httpsServerOptions, app)
        .listen(httpsPort, () => {
            console.log(`Jsonata HTTPS server listening on port ${httpsPort}`)
        })
}

process.on('SIGINT', shutDown);
process.on('SIGTERM', shutDownNow);

let connections = [];

if (httpServer) {
    httpServer.on('connection', connection => {
        connections.push(connection);
        connection.on('close', () => connections = connections.filter(curr => curr !== connection));
    });
}

if (httpsServer) {
    httpsServer.on('connection', connection => {
        connections.push(connection);
        connection.on('close', () => connections = connections.filter(curr => curr !== connection));
    });
}

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

    const closePromises = []
    if (httpServer) {
        closePromises.push(new Promise((resolve, _) => {
            httpServer.close(() => {
                console.log('Closed out remaining HTTP connections');
                resolve()
            });
        }))
    }
    if (httpsServer) {
        closePromises.push(new Promise((resolve, _) => {
            httpsServer.close(() => {
                console.log('Closed out remaining HTTPS connections');
                resolve()
            });
        }))
    }

    Promise.all(closePromises).then(() => {
        console.log('Shutting down tracing...');
        batchSpanProcessor.shutdown().then(() => {
            process.exit(0);
        });
    })

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
