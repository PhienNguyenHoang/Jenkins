def COLOR_MAP = ['SUCCESS': 'good', 'FAILURE': 'danger', 'UNSTABLE': 'danger', 'ABORTED': 'danger']
SLACK_CHANNEL='#devops'

pipeline {
  agent any 
  options {
	  ansiColor('xterm')
    disableConcurrentBuilds()
  }

  environment {
	  TIMESTAMP = """${sh(
				returnStdout: true,
				script: 'date --utc +%Y%m%d_%H%M%SZ'
				).trim()}"""
    KUBECONFIG="/home/cse/.kube/config"
  }

  parameters {
    string (name: "gitBranch", defaultValue: "prestage", description: "Branch to build")
    string (name: "git_sha", defaultValue: "HEAD", description: "sha to build")
  }

  triggers {
    GenericTrigger(
      genericVariables: [
        [key: 'gitBranch', value: '$.ref'],
        [key: 'git_sha', value: '$.after'],
        //[key: 'changed_files', value: '$.commits[*].["modified","added","removed"][*]']
      ],

      causeString: 'Triggered on $gitBranch',

      token: 'trueprofile-api-build',

      printContributedVariables: true,
      printPostContent: true,

      silentResponse: false,

      regexpFilterText: '$gitBranch',
      regexpFilterExpression: '^refs/heads/prestage'
      )
  }

  stages {
    stage('Checkout') {
      steps {
        dir('var/www/') {
          checkout ( [$class: 'GitSCM',
            extensions: [[$class: 'CloneOption', timeout: 30]],
            branches: [[name: "${gitBranch}" ]],
            userRemoteConfigs: [[
              credentialsId: "2ee2a10e-9745-476d-bcc5-0c0062b1de39",
              url: "git@github.com:dataflowplus/api-trueprofile.io.git"]]])
        }
      }
    }

    stage('Composer Install') {
      steps {
        dir('var/www/') {
          sh """
            cp \${WORKSPACE}/deployment/environment-pre-stage/api/Dockerfile .
            cp \${WORKSPACE}/deployment/environment-pre-stage/api/docker-nginx-site.conf .
            cp ~/.ssh/id_rsa .
            aws s3 cp s3://trueprofile-infrastructure/deployment/environment-pre-stage/trueprofile/api/.env.local .env.local
            aws ecr get-login  --no-include-email | xargs -0 bash -c
            docker build -t 780917974971.dkr.ecr.ap-southeast-1.amazonaws.com/trueprofile-backend:prestage-\${BUILD_NUMBER} .
            docker push 780917974971.dkr.ecr.ap-southeast-1.amazonaws.com/trueprofile-backend:prestage-\${BUILD_NUMBER}
            docker rmi 780917974971.dkr.ecr.ap-southeast-1.amazonaws.com/trueprofile-backend:prestage-\${BUILD_NUMBER}
          """
        }
      }
    }

	  stage('Deploy') { 
	    steps {
        dir('deployment/environment-pre-stage/api') {
          sh """
            DOCKER_TAG="prestage"-\${BUILD_NUMBER}              
            sed -i "s/__docker-tag__/\$DOCKER_TAG/g" values.yaml
            sed -i "s/__hostname__/api.test.trueprofile.io/g" values.yaml
            sed -i "s/__docker-tag__/\$DOCKER_TAG/g" values-worker.yaml
            sed -i "s/__docker-tag__/\$DOCKER_TAG/g" values-cron.yaml
            
            helm upgrade prestage-backend --install \${WORKSPACE}/k8s-charts/hosting/backend-0.1.6.tgz -n pre-stage -f values.yaml
           # helm upgrade prestage-worker --install \${WORKSPACE}/k8s-charts/hosting/backend-0.1.6.tgz -n pre-stage -f values-worker.yaml
           # helm upgrade prestage-cron --install \${WORKSPACE}/k8s-charts/hosting/backend-0.1.6.tgz -n pre-stage -f values-cron.yaml
          """
        }
      }
	  }
  } // End stages

  post {
    always {
      cleanWs()
      slackSend channel: "${SLACK_CHANNEL}", color: COLOR_MAP[currentBuild.currentResult] , message: "Deployment *`${currentBuild.currentResult}`* - ${env.JOB_NAME} #${env.BUILD_NUMBER} (<${env.BUILD_URL}/console|Open>)\nGit Branch: ${gitBranch} \n ${getChangeString()}"
    }
  } 

} // End pipeline  
