# Go.cd Docker Step Plugin

Plugin for running scripts inside a Docker container.
The behaviour is inspired by CodeFresh docker steps, the idea is to
act similar to the script-executor task, but instead of running on the agent,
the script is run inside an arbitrary container.

There is also support for spinning up companion containers, typically for
test databases etc.  Those "services" are assumed to be completely configured
using environment variables.

## What to expect

This is built and tested for running inside the gocd-agent-dind docker image,
and as such assumes a Linux system and docker available.

Container images used should support being given a script to run as it's
command, without having to play with the entrypoint or other weird stuff.

Service images are given the environment, and nothing else.
  
## Usage

The mandatory configuration is the image to run, and the commands to
run in it.  Commands are run in bash, with 'set -ex' applied.  I.e. it will bail
out on errors, and you can see in the task log what you did.

Optional configuration is "pull", that can be set to false in order to not
try to pull images.  This allows the task to work with images that have not
been pushed to a registry.  Remember that turning off pull applies to all
images, also services.

A simple task to build a node application could be something like:
```yaml
tasks:
  - plugin:
      configuration:
        id: docker-step
        version: 0.1.0
      options:
        image: node:16
        commands: |
          yarn
          yarn build
          yarn test
```

The pipeline working directory is mounted on `/working`, which is also the
working directory when the commands start.  The container runs as the same
user as the agent, to avoid making a mess of file permissions etc.

When running more complex tests, e.g. testing a Django build, services are
needed.  A service does not get any filesystem mounted, nor is the user
changed.  But everything is run in a separate network so things are reachable
using the service name given.  If multiple services are needed, just supply
them one on a line, the format is `<name>;<image>`:

```yaml
jobs:
  build-and-test-django:
    environment_variables:
      POSTGRES_DB: test
      POSTGRES_USER: test
      POSTGRES_PASSWORD: test
      DB_HOST: pg
    tasks:
      - script: |
          set -e
          docker build -t django-app:test .
          docker pull postgres:latest
      - plugin:
          configuration:
            id: docker-step
            version: 0.1.0
          options:
            image: django-app:test
            pull: 'false'
            commands: |
              cd /app
              ./manage.py test
            services: |
              pg;postgres:latest
```

## Credits

This plugin owes quite a bit to the docker-exec plugin by Christopher Arnold,
which showed how to use the various libraries.  Some structure is still left,
but this one works quite differently by now.
