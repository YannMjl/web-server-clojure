# specify the base imagine we want to use for this container
FROM ubuntu:latest

# author or owner of this container
MAINTAINER Yann Mulonda (yannmjl@gmail.com)

# install and update container packages
RUN apt-get -y update && apt-get install -y


# If you're reading this and have any feedback on how this image could be
# improved, please open an issue or a pull request so we can discuss it!
