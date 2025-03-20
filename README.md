# Teralizer

## Docker
To run the Docker container use the `docker compose up` command. This will start Teralizer and an additional Adminer 
container that can be used to access the database. Docker compose will store all generated data in the `docker-data` folder in the current directory. 
To access the database on its own, the adminer instance can be started as follows:

~~~shell
docker compose up adminer
~~~

The database can be accessed using the password `teralizer` under the 
following URL [http://localhost:18080/?sqlite=&username=teralizer&db=%2Fapp%2Fdatabase%2Fdb.sqlite](http://localhost:18080/?sqlite=&username=teralizer&db=%2Fapp%2Fdatabase%2Fdb.sqlite).
