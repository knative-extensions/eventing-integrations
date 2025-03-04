# JSON Transformations

```text

source or webhook -> transform -> broker

trigger -> transform -> sink or webhook

broker -> trigger -> (request)  -> transform
broker <- trigger <- (response) <-
```

## Development

Assuming current working directory is `transform-jsonata`

```shell
npm run dev

npm run dev-kubevirt # to inject a different example transformation

npm run dev-tls      # to start the HTTP and HTTPS Servers
npm run dev-tls-only # to start the HTTPS Server only
```

### Tracing

```shell
docker run --rm -d -p 9411:9411 openzipkin/zipkin

npm dev-zipkin
```

## Building

Assuming current working directory is `transform-jsonata`

```shell
IMAGE_NAME="${KO_DOCKER_REPO}/transform-jsonata"
docker build -t "${IMAGE_NAME}" -f Dockerfile . 
docker push "${IMAGE_NAME}"
```

### Running

```shell
docker run --user $(id -u):$(id -g) -ti --rm --mount src="$(pwd)/examples",dst=/var/examples,type=bind -e NODE_ENV=development -e JSONATA_TRANSFORM_FILE_NAME=/var/examples/ce_apiserversource_kubevirt.jsonata -p 8080:8080 "${IMAGE_NAME}"
```

### Testing the example

```shell
curl -v -XPOST http://localhost:8080 -d @examples/ce_apiserversource_kubevirt.json
```

```shell
curl -v -XPOST http://localhost:8080 \
    -H "content-type: application/json"  \
    -H "ce-specversion: 1.0"  \
    -H "ce-source: my/curl/command"  \
    -H "ce-type: my.demo.event"  \
    -H "ce-id: 0815"  \
    -d '{"name":"curl"}'
```

## Testing transformations

https://try.jsonata.org/