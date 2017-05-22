# DLUBM LD-Fu Evaluation
## Requirements
### AWC CLI
* [AWS](https://aws.amazon.com/)
* [AWS CLI](https://aws.amazon.com/cli/)
* [AWS CLI User Guide](https://docs.aws.amazon.com/cli/latest/userguide/)
* [AWS CLI User Guide Installation](https://docs.aws.amazon.com/cli/latest/userguide/installing.html)
* [AWS CLI User Guide Configuration](https://docs.aws.amazon.com/cli/latest/userguide/cli-chap-getting-started.html)
* [AWS EC2 User Guide Network Security](https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/using-network-security.html)

### Docker & Docker Machine
* [Docker](https://docker.com/)
* [Docker Documentation Machine](https://docs.docker.com/machine/)
* [Docker Documentation Engine Reference Docker Daemon Socket Options](https://docs.docker.com/engine/reference/commandline/dockerd/#daemon-socket-option)
* [Docker Documentation Engine Swarm Protocols and Ports](https://docs.docker.com/engine/swarm/swarm-tutorial/#open-protocols-and-ports-between-the-hosts)

## Usage
Following steps are required to configure, deploy, and terminate the DLUBM environment.
While everything could be handled by two or three scripts, these are split for better readability in smaller scripts that handle related functionality.
In addition, LD-Fu is evaluated against the DLBM environment.

### Configuration
AWS CLI must be configured with appropriate credentials.
For configuring the environment, check the `configuration` file.
### Deployment
#### Create AWS EC2 Security Group
Create the AWS EC2 security group.

    $ ./awsec2-securitygroup-create

----
Create the AWS EC2 security group to be used by Docker Swarm instances and the evaluation instance.
The security group has inbound rules to open ports for accessing instances via SSH or HTTP, controlling the Docker Daemon, and communication between Docker Swarm managers/workers.
Following ports are opened:

* Open ports required for access
    * TCP port 22 for SSH
    * TCP port 80 for HTTP
    * TCP port 8080 for HTTP
* Open ports required for communication with the Docker Daemon
    * TCP port 2376 for encrypted communication with the daemon
* Open ports required for communication within the Docker Swarm
    * TCP port 2377 for cluster management communications
    * UDP port 4789 for overlay network traffic
    * TCP and UDP port 7946 for communication among nodes

#### Start AWS EC2 Instances
Start the AWS EC2 instances.

    $ ./awsec2-instances-start

----
Start AWS EC2 instances according to the configuration.
First, instances for Docker Swarm managers are created, then instances for Docker Swarm workers, and then the instance for evaluation.
The security group created above is assigned to all instances.

#### Assemble Docker Swarm
Assemble the Docker Swarm.

    $ ./docker-swarm-assemble

----
Assemble the Docker Swarm by, initializing the Swarm with the first manager instance, then joining the Swarm with all other manager instances as managers, and last joining the Swarm with all worker instances as workers.

#### Create Reverse Proxy Network
Create the reverse proxy network.

    $ ./docker-network-reverseproxy-create

----
Create the reverse proxy network that will be used by the reverse proxy container and all othercontainers.

#### Create Traefik Service
Create the Traefik service.

    $ ./docker-service-traefik-create

----
Create the Traefik service that is restricted to run at a manager node.

#### Deploy DLUBM Stack
Deploy the DLUBM stack.

    $ ./docker-stack-dlubm-deploy

----
Deploy the DLUBM stack by setting the Docker environment to a Docker Swarm manager and deploying the DLUBM stack.

### Evaluation
#### Prepare Evaluation Instance
Prepare the evaluation instance.

    $ ./evaluation-preparation

----
Prepare the evaluation instance by installing all required dependencies and LD-Fu.
Note that the instance restarts after preparation.

#### Execute Experiment
Execute the experiment.

    $ ./evaluation-experiment

----
Execute the experiment against the deployed DLUBM instance.
### Termination
#### Remove DLUBM Stack
Remove the DLUBM stack.

    $ ./docker-stack-dlubm-remove

#### Remove Traefik Service
Remove the Traefik service.

    $ ./docker-service-traefik-remove

#### Remove Reverse Proxy Network
Create the reverse proxy network.

    $ ./docker-network-reverseproxy-remove

#### Disassemble Docker Swarm
Disassemble the Docker Swarm.

    $ ./docker-swarm-disassemble

#### Stop AWS EC2 Instances
Stop the AWS EC2 instances.

    $ ./awsec2-instances-stop

#### Remove AWS EC2 Security Group
Remove the AWS EC2 security group.

    $ ./awsec2-securitygroup-remove
