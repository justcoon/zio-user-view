# zio user view

user/department view aggregation (via kafka streams)

see also [akka-typed-user](https://github.com/justcoon/akka-typed-user)

# required
* kafka

### application setup

multi nodes VMs arguments

node 1

```
-Drest-api.port=8030 -Dgrpc-api.port=8040 -Dprometheus.port=9050 -Dkafka.state-dir=/{your-local-dir}/kafka-streams/0
```
node 2

``` 
 -Drest-api.port=8031 -Dgrpc-api.port=8041 -Dprometheus.port=9051 -Dkafka.state-dir=/{your-local-dir}/kafka-streams/1
```
node 3

``` 
 -Drest-api.port=8032 -Dgrpc-api.port=8042 -Dprometheus.port=9052 -Dkafka.state-dir=/{your-local-dir}/kafka-streams/2
```