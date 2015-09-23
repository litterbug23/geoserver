# How to use this Dockerfile

You can build a docker image based on this Dockerfile. This image will contain Fiware GIS Data Provider components composed of Tomcat7 instance, exposing port `8080` serving Geoserver and reference client. This requires that you have [docker](https://docs.docker.com/installation/) installed on your machine.

If you just want to have an GIS Data Provider running as quickly as possible jump to section *The Fastest Way*.

If you want to know what is behind the scenes of our container you can go ahead and read the build and run sections.

## The Fastest Way

### Run the container

docker run -t -i juhahyva/gisdataprovider

You may define forwarded ports with -p flag
docker run -t -p 9090:8080 -i juhahyva/gisdataprovider


This pulls the image from the Docker Registry instead of building your own. Keep in mind though that everything is run locally. 

After image is downloaded server instance will response from: [http://localhost:9090/geoserver/web](http://localhost:9090/geoserver/web)

Reference client will be at [http://localhost:9090/GIS/](http://localhost:9090/GIS/)

> **Warning**
> Everything you do with GIS Data Provider when dockerized is non-persistent. *You will lose all your data* if you turn off the GIS Data Provider container.
> If you want to prevent this from happening mount data directory as a [volume](https://docs.docker.com/userguide/dockervolumes/)

## Build the image

This is an alternative approach to the one presented in the previous section. You do not need to go through these steps if you have downloaded image from Dockerhub. The end result will be the same, but this way you have a bit more of control of what's happening.

You only need to do this once you have downloaded Dockerfile to your system:

    sudo docker build -t gisdataprovider .

> **Note**
> If you do not want to have to use `sudo` in this or in the next section follow [these instructions](http://askubuntu.com/questions/477551/how-can-i-use-docker-without-sudo).


The parameter `-t gisdataprovider` gives the image a name. This name could be anything, or even include an organization like `-t org/gisdataprovider`. This name is later used to run the container based on the image.

If you want to know more about images and the building process you can find it in [Docker's documentation](https://docs.docker.com/userguide/dockerimages/).
    
### Run the container

The following line will run the container exposing docker port `8080` to host port `9090`.

      sudo docker run -t -p 9090:8080 gisdataprovider

If you did not build the image yourself and want to use the one on Docker Hub use the following command:

      sudo docker run -t -p 9090:8080 juhahyva/gisdataprovider

> **Note**
> Keep in mind that if you use this last command you get access to the tags and specific versions of GIS Data Provider.

As a result of this command, there is a GIS Data Provider listening on port 9090 on localhost. Try to see if it works now with

    curl localhost:9090/geoserver/web/
