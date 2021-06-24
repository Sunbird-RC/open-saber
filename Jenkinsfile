node {
    try {
        def branchname = 'open-saber-rc-2'
        stage('Clone repository') {
            git([url: 'https://github.com/tejash-jl/open-saber', branch: "${branchname}"])
        }

        stage('Compile And Test'){
            sh """sh configure-dependencies.sh"""
            sh """ docker run -v "$HOME/.m2":/root/.m2  -v "\$(pwd)":/src -w /src/java java:8 ./mvnw clean install -nsu"""
            sh """rm -rf target"""
            sh """mkdir target"""
            dir('target') {
              sh """jar -xvf ../java/registry/target/registry.jar"""
            }
            step([$class: 'JacocoPublisher', exclusionPattern: '**/model/**,**/helpers/**,**/config/**,**/configs/**,**/controllers/**,**/models/**,**/exceptions/**,**/messaging/**,**/response/**,**/utils/**,**/migration/**,**/TestApplication.class,**/TestKafkaConfiguration.class,**/HealthCheck.class,**/TestSubmissionPipelineApplicationn.class,**/TestApplicationConfiguration.class,**/migration/**'])
        }

        stage('Build image') {
            app = docker.build("tejashjl/open-saber-rc",".")
            claimApp = docker.build("tejashjl/open-saber-claim-ms","java/claim")
        }

        stage('Test image') {
            app.withRun('-p 8010:8081') {c ->
                sh """#!/bin/bash
                env;
                i=0;
                while [[ \$i -lt 120 ]] ; do let i=i+1; sleep 1; status=`curl -I localhost:8010/health 2>/dev/null | grep 'HTTP/1.1 200' | wc -l`;if [ \$status -ge 1 ];then echo '\nTested Successfully';exit 0;else printf '.';  fi;done; exit 1;"""
            }
        }


        stage('Push image') {
            docker.withRegistry('https://registry.hub.docker.com', 'dockerhub') {
                app.push("${env.BUILD_NUMBER}")
                app.push("latest")
           }
           docker.withRegistry('https://registry.hub.docker.com', 'dockerhub') {
               claimApp.push("${env.BUILD_NUMBER}")
               claimApp.push("latest")
          }
        }

        stage('Deploy image') {
            sh "ssh dileep@40.80.94.137 'kubectl get pods -n ndear'"
            sh "ssh dileep@40.80.94.137 'kubectl set image deployment/registry registry=tejashjl/open-saber-rc:${env.BUILD_NUMBER} --record --namespace=ndear'"
            sh "ssh dileep@40.80.94.137 'kubectl set image deployment/claim-ms claim-ms=tejashjl/open-saber-claim-ms:${env.BUILD_NUMBER} --record --namespace=ndear'"

        }

    }
    catch (err) {
        currentBuild.result = "FAILURE"
        throw err
    }

}
