# Go.cd Docker Step Plugin

Plugin for running scripts inside a Docker container.
The behaviour is inspired by CodeFresh docker steps, the idea is to
act similar to the script-executor task, but instead of running on the agent,
the script is run inside an arbitrary container.

## What to expect

This is built and tested for running inside the gocd-agent-dind docker image,
and as such assumes a Linux system and docker available.

Container images used should support being given a script to run as it's
command, without having to play with the entrypoint or other weird stuff.

Service images are given the environment, and nothing else.
  
## Usage

TODO: Add yaml sample.

## Credits

This plugin owes quite a bit to the docker-exec plugin by Christopher Arnold,
which showed how to use the various libraries.  Some structure is still left,
but this one works quite differently by now.
