SHELL := /bin/bash

build:
	gradle clean 
	gradle build

docker:
	docker build -t aaa/njbrtpxy .

all: build docker
