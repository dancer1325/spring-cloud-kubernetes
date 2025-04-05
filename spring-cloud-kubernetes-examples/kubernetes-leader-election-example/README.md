# Spring Cloud Kubernetes leader election example

* goal
  * deploy an application -- , via Fabric8 Maven Plugin, to -- a Kubernetes cluster 
  * implements Spring Integration' leader election API -- via -- Kubernetes ConfigMap /
    * SAME application's MULTIPLE instances compete -- for a -- leadership of a specified role /
      * 1! leader / 
        * receive `OnGrantedEvent` application event -- with a -- leadership `Context`
        * FIRST one
        * remains as leader TILL 
          * its instance disappears | cluster OR
          * it yields its leadership 
      * ALL instances try -- PERIODICALLY, to get a -- leadership /
        * OLD leader -- receives -- `OnRevokedEvent` application event

## How to set up?

* -- via -- [Minikube](https://minikube.sigs.k8s.io/docs/start/)
  * download & install
  * `minikube start`
  * configure environment variables / -- point to the -- Minikube's Docker daemon
    ```
    eval $(minikube docker-env)
    ```

## How to run?
* 
    ```
    kubectl apply -f leader-role.yml
    kubectl apply -f leader-rolebinding.yml
    ``` 
  * allows
    * accessing ConfigMap user

* 
    ```
    ./mvnw spring-boot:build-image -Dspring-boot.build-image.imageName=org/kubernetes-leader-election-example
    ```
  * build an image -- via -- Spring Boot Build Image Plugin
* `kubectl apply -f list.yml`
  * deploy 1! application instance | cluster
    * -> the leader
* `export SERVICE_URL=$(minikube service kubernetes-leader-election-example --url)`
  * environment variable / access application
* `curl $SERVICE_URL`
  * get leadership information 
    * 's output
      ```
      I am 'kubernetes-leader-election-example-1234567890-abcde' and I am the leader of the 'world'
      ```
  * if MULTIPLE replicas | cluster -> access SOME replica -- depending on the -- Kubernetes load balancing configuration
* `curl -X PUT $SERVICE_URL`
  * yield the leadership 
* `curl $SERVICE_URL`
  * check leadership information AGAIN
  * if ANOTHER instance got the leadership -> DIFFERENT output
