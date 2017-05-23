# DLUBM LD-Fu Evaluation
## Requirements
### AWC CLI
The Amazon Web Services (AWS) Command Line Interface (CLI) is utilized to create and configure a security group for our environment.
By adding inbound rules for specific ports, we enable access to instances and communication within Docker Swarms.
In addition, Docker Machine uses AWS CLI credentials to connect to AWS EC2.
Documentation is provided, for example, at:

* [AWS](https://aws.amazon.com/)
* [AWS CLI](https://aws.amazon.com/cli/)
* [AWS CLI User Guide](https://docs.aws.amazon.com/cli/latest/userguide/)
* [AWS CLI User Guide Installation](https://docs.aws.amazon.com/cli/latest/userguide/installing.html)
* [AWS CLI User Guide Configuration](https://docs.aws.amazon.com/cli/latest/userguide/cli-chap-getting-started.html)
* [AWS EC2 User Guide Network Security](https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/using-network-security.html)

### Docker Machine
The Docker Machine tool is utilized to handle the management of dockerized EC2 instances.
Documentation is provided, for example, at:

* [Docker](https://docker.com/)
* [Docker Documentation Machine](https://docs.docker.com/machine/)
* [Docker Documentation Engine Reference Docker Daemon Socket Options](https://docs.docker.com/engine/reference/commandline/dockerd/#daemon-socket-option)
* [Docker Documentation Engine Swarm Protocols and Ports](https://docs.docker.com/engine/swarm/swarm-tutorial/#open-protocols-and-ports-between-the-hosts)

### Dynamic DNS
A reverse proxy in combination with a wildcard-enabled dynamic DNS domain entry are utilized to ease the deployment.
Therefore, a configurable domain entry is required, for example, provided by

* [No-IP](https://www.noip.com/remote-access)
* [Dyn](https://dyn.com/dns)

or similar providers.




## Quickstart
We provide high-level scripts for composing, deploying, and terminating the Distributed LUBM benchmark environment.
After deploying, we are evaluating the Linked Data query engine Linked Data-Fu (LD-Fu).
For detailed explanations see the *Usage* section below.

----
Configure the DLUBM environment.
We require a properly configured AWS CLI with permission to manage AWS EC2 instances and a Dynamic DNS domain entry with enabled wildcard for subdomains designated to provide URIs for the DLUBM environment.
Copy the example configuration file as base.

    $ cp configuration.example configuration

Edit the configuration file and set at least the *DOMAIN* parameter with the Dynamic DNS domain entry.
Take a look at the amount and type of EC2 instances to be initialized as well as the configuration of the DLUBM.
With the current example configuration, one EC2 instance of type *t2.small* for evaluation, one EC2 instance of type *m4.xlarge* for a Swarm Manager, and 18 EC2 instances of type *t2.small* for Docker Swarm workers are initiated that host a DLUBM with 100 containers.
Currently, the amount of 20 EC2 instances is the maximum for instances running in parallel without requesting a limit increase.

----
Compose the environment.

    $ ./compose

Actions:

1. Generates based on the configuration a composition of containers saved in the *docker-compose.yml* file

----
Deploy the DLUBM environment.

    $ ./deploy

Actions:

1. Creates an AWS EC2 security group with inbound rules for required ports
1. Starts the AWS EC2 instances in configured amounts and with configured types
1. Prepares the evaluation instance by setting up the system for experiments
1. Assembles the Docker Swarm by initiating the first manager node and joining with further managers als well as worker nodes
1. Creates an overlay network used by the reverse proxy for directing requests at (sub)domains to responsible containers
1. Creates the reverse proxy service, currently a Traefik reverse proxy
1. Deploys the DLUBM stack, i.e., deploys the composition generated beforehand to the Docker Swarm

----
Update the Dynamic DNS domain entry.
Use an external IP of an instance, for example, of the first manager of the Docker Swarm.

    $ ./scripts/docker-swarm-ips

Actions:

1. Lists all internal and external IPs of Docker Swarm nodes

----
Evaluate LD-Fu against the DLUBM environment.

    $ ./evaluate

Actions:

1. Evaluates by copying all required files to the evaluation instance, executing the experiments, and copying all files, including the results, back to the local machine

----
Terminate the DLUBM environment.

    $ ./terminate

Actions:

* Executes all actions of the deploy step in reverse order
    * Currently, the security group is not automatically removed but must be removed manually later once all instances are terminated



## Usage
In the following, we document in detail all steps are required to configure, compose, deploy, and terminate the DLUBM environment.
In addition, LD-Fu is evaluated against the DLBM environment.

### Configuration
#### AWS CLI
AWS CLI must be configured with appropriate credentials, i.e., secret key id and secret access key, for a user that is permit to manage EC2 security groups and instances.

    $ aws configure

If you are not familiar with AWS, in particular AWS EC2 and AWS CLI, take a look at [AWS Getting Started](https://aws.amazon.com/documentation/gettingstarted/), [AWS EC2 Getting Started](https://aws.amazon.com/ec2/getting-started/), and [AWS CLI User Guide](https://docs.aws.amazon.com/cli/latest/userguide/).

#### Environment
For configuring the environment, create the `configuration` file by either copying the `configuration.example` file or creating a new one based on the example file.

Configuration parameters:

* DOMAIN
    * The domain entry. **Important:** This entry must correspond to the dynamic DNS domain entry with enabled wildcard. The composition is generated with respect to this domain entry and in addition the reverse proxy service is configured to handle requests to this domain as well as subdomains.
* TODO

### Composition
#### Generate DLUBM Composition
Generate the DLUBM Composition.

    $ ./scripts/dlubm-compose

----
Generate the DLUBM Composition that will be saved in the *docker-compose.yml* file.

### Deployment
#### Create AWS EC2 Security Group
Create the AWS EC2 security group.

    $ ./scripts/awsec2-securitygroup-create

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

    $ ./scripts/awsec2-instances-start

----
Start AWS EC2 instances according to the configuration.
First, instances for Docker Swarm managers are created, then instances for Docker Swarm workers, and then the instance for evaluation.
The security group created above is assigned to all instances.

#### Prepare Evaluation Instance
Prepare the evaluation instance.

    $ ./scripts/evaluation-preparation

----
Prepare the evaluation instance by installing all required dependencies and LD-Fu.
Note that the instance restarts after preparation.


#### Assemble Docker Swarm
Assemble the Docker Swarm.

    $ ./scripts/docker-swarm-assemble

----
Assemble the Docker Swarm by initializing the Swarm with the first manager instance, then joining the Swarm with all other manager instances as managers, and last joining the Swarm with all worker instances as workers.

#### Create Reverse Proxy Network
Create the reverse proxy network.

    $ ./scripts/docker-network-reverseproxy-create

----
Create the reverse proxy network that will be used by the reverse proxy container and all other containers.

#### Create Reverse Proxy Service
Create the reverse proxy service.

    $ ./scripts/docker-service-traefik-create

----
Create the reverse proxy service by, currently, using a proper configured Traefik.
The service is restricted to run at a manager node to retrieve required metadata.
It uses the reverse proxy network to redirect requests at (sub)domains to responsible containers.

#### Deploy DLUBM Stack
Deploy the DLUBM stack.

    $ ./scripts/docker-stack-dlubm-deploy

----
Deploy the DLUBM stack by setting the Docker environment to a Docker Swarm manager and deploying the DLUBM composition that has been previously generated and saved in the *docker-compose.yml* file.

#### Update Dynamic DNS
Retrieve instance IP adresses.

    $ ./scripts/docker-swarm-ips

----
Retrieve instance IP adresses and update the dynamic DNS domain entry to point to an external IP of the swarm, e.g., the first manager instance.

**Important:** The domain must be the domain set in the configuration and must have wildcard enabled. If not, you must remove the stack and reverse proxy service, update the configuration, recreate the composition, recreate the reverse proxy service, and redeploy the stack.

Check if the environment is working by resolving the domain in a browser. Port 80 should provide the global university links and port 8080 should provide an overview about the routing of Traefik.

### Evaluation
#### Execute Experiment
Execute the experiment.

    $ ./scripts/evaluation-experiment

----
Execute the experiment against the deployed DLUBM instance.
### Termination
#### Remove DLUBM Stack
Remove the DLUBM stack.

    $ ./scripts/docker-stack-dlubm-remove

#### Remove Traefik Service
Remove the Traefik service.

    $ ./scripts/docker-service-traefik-remove

#### Remove Reverse Proxy Network
Create the reverse proxy network.

    $ ./scripts/docker-network-reverseproxy-remove

#### Disassemble Docker Swarm
Disassemble the Docker Swarm.

    $ ./scripts/docker-swarm-disassemble

#### Stop AWS EC2 Instances
Stop the AWS EC2 instances.

    $ ./scripts/awsec2-instances-stop

#### Remove AWS EC2 Security Group
Remove the AWS EC2 security group.

    $ ./scripts/awsec2-securitygroup-remove
