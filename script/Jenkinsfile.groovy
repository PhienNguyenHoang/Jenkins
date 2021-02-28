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

  triggers {
    GenericTrigger(
      genericVariables: [
        [key: 'gitBranch', value: '$.ref'],
        [key: 'git_sha', value: '$.after'],
        //[key: 'changed_files', value: '$.commits[*].["modified","added","removed"][*]']
      ],

      causeString: 'Triggered on $gitBranch',

      // token: 'trueprofile-api-build',

      // printContributedVariables: true,
      // printPostContent: true,

      // silentResponse: false,

      // regexpFilterText: '$gitBranch',
      // regexpFilterExpression: '^refs/heads/prestage'
      )
  }


  stages {

    stage('Clean workspace') {
      steps {
        deleteDir()
      }
  }

    stage('Checkout') {
      steps {
        dir('./') {
          // checkout ( [$class: 'GitSCM',
          //   extensions: [[$class: 'CloneOption', timeout: 30]],
          //   branches: [[name: "${gitBranch}" ]],
          //   userRemoteConfigs: [[
          //     credentialsId: "6e08bd98-e13c-484a-945f-57c278ab6791",
          //     url: "https://github.com/ititiu14078/sample-project.git"]]])
          git credentialsId: '6e08bd98-e13c-484a-945f-57c278ab6791', url: 'https://github.com/ititiu14078/sample-project.git'
        }
      }
    }

    stage('Build Image') {
      steps {
        dir('./') {
          sh """
            docker build -t phienhoangnguyen/thesis-phien-2021:\${BUILD_NUMBER} .
            docker login -u phienhoangnguyen -p nothing@@
            docker push phienhoangnguyen/thesis-phien-2021:\${BUILD_NUMBER}
          """
        }
      }
    }

    //  stage('Push Image') {
    //   steps {
    //     withDockerRegistry([ credentialsId: "d783c462-a0ed-4d1c-9df3-aca9ef837a7e", url: "https://hub.docker.com/repository/docker/phienhoangnguyen/thesis-phien-2021" ]) {
    //       bat "docker push phienhoangnguyen/thesis-phien-2021:\${BUILD_NUMBER}"
    //     }
    //   }
    // }

	  stage('Deploy') { 
	    steps {
        dir('./') {
          sh """
            helm repo update
            helm upgrade --install phien-java-app --set image.repository=phienhoangnguyen/thesis-phien-2021 --set image.tag=\${BUILD_NUMBER} --set image.pullPolicy=Always --set tomcatPassword=2MNxLHqfIg bitnami/tomcat
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

