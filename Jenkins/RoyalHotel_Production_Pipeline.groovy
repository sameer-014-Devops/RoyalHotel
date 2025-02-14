/* NOTE: Make sure add the variables at node levels

#Variable
  App_Name = royalhotel
  Container_Name = ${User_Name}-${App_Name}-container
  Default_Ver = 1.0.1
  Deploy_Name = ${User_Name}-${App_Name}-deploy
  KubeM_Pvt_IP =
  KubeW01_Pub_IP =
  KubeW02_Pub_IP =
  Prod_Workspace = /home/${User_Name}/workspace/RoyalHotel_Production_Pipeline
  Build01_Pvt_IP =
  BN01_Path = /home/${User_Name}/workspace/${JOB_NAME}
  Test_Server_Path = /opt/tomcat/webapps/
  Test_Server_Pub_IP = 
  Test_Server_Pvt_IP =
  User_Name = skmirza
********************************************************************************************************************************************************************************
********************************************************************************************************************************************************************************
*/
pipeline{
    agent none
    parameters {
        string(name: 'new_ver', defaultValue: 'latest', description: 'Deploy Version from Test Pipeline')
    }
    environment {
        DOCKERHUB_CREDENTIALS = credentials('dockerhublogin')
        Docker_Build = false
        Docker_Login = false
        Docker_Push = false
        Deploy_Main = false
    }
    stages {
        stage('Docker_Build') {
            agent {
                label 'BN01'
            }
            steps {
                echo '*********Building Docker Image*********'

                script {
                    def latestImageExists = sh(script: """docker images -q $DOCKERHUB_CREDENTIALS_USR/$User_Name-$App_Name-img:latest""", returnStdout: true).trim()

                    if (latestImageExists) {
                        echo 'Docker Image with the latest tag already exists. Removing the existing image...'
                        sh """docker rmi -f $DOCKERHUB_CREDENTIALS_USR/$User_Name-$App_Name-img:latest"""
                    } else {
                        echo 'Docker Image with the latest tag does not exist...'
                    }

                    def versionedImageExists = sh(script: """docker images -q $DOCKERHUB_CREDENTIALS_USR/$User_Name-$App_Name-img:${params.new_ver}""", returnStdout: true).trim()

                    if (versionedImageExists) {
                        echo 'Docker Image with the specified version already exists. Removing the existing image...'
                        sh """docker rmi -f $DOCKERHUB_CREDENTIALS_USR/$User_Name-$App_Name-img:${params.new_ver}"""
                    } else {
                        echo 'Docker Image with the specified version does not exist...'
                    }

                    sh 'docker logout'
                    sh """docker build -t $DOCKERHUB_CREDENTIALS_USR/$User_Name-$App_Name-img:${params.new_ver} ."""
                    sh """docker tag $DOCKERHUB_CREDENTIALS_USR/$User_Name-$App_Name-img:${params.new_ver} $DOCKERHUB_CREDENTIALS_USR/$User_Name-$App_Name-img:latest"""
                    sh 'docker images'

                    def latestImgExists = sh(script: """docker images -q $DOCKERHUB_CREDENTIALS_USR/$User_Name-$App_Name-img:latest""", returnStdout: true).trim()
                    if (latestImgExists) {
                        echo '*********Docker Image Build SUCCESSFUL*********'
                        Docker_Build = true
                    } else {
                        error '*********Docker Image Build FAILED*********'
                    }
                }
            }
            post {
                success {
                    script {
                        echo '########## Docker build is SUCCESSFUL ##########'
                    }
                }
                failure {
                    script {
                        echo '########## Docker build is FAILED ##########'
                    }
                }
            }
        }
        stage ('Docker_Login'){
            agent {
                label 'BN01'
            }
            when{
                expression { Docker_Build == true }
            }
            steps {
                echo 'Login to Docker Hub'
                sh 'echo $DOCKERHUB_CREDENTIALS_PSW | docker login -u $DOCKERHUB_CREDENTIALS_USR --password-stdin'
                script {
                    if (sh(script: 'docker info | grep -i "Username: $DOCKERHUB_CREDENTIALS_USR"', returnStatus: true) == 0) {
                        echo '*********Docker Hub Login SUCCESSFUL*********'
                        Docker_Login = true
                    } else {
                        error '*********Docker Hub Login FAILED*********'
                    }
                }
            } 
            post{
                success{
                    script{
                        echo '########## Docker Login is SUCCESSFUL ##########'
                    }
                }
                failure{
                    script{
                        echo '########## Docker Login is FAILED ##########'
                    }
                }
            }
        }
        stage ('Docker_Push'){
            agent {
                label 'BN01'
            }
            when{
                expression { Docker_Login == true }
            }
            steps {
                echo 'Pushing Docker Image to Docker Hub'
                script {
                    echo "********Pushing Docker Image With The Version ${params.new_ver}********"
                    sh """docker push $DOCKERHUB_CREDENTIALS_USR/$User_Name-$App_Name-img:${params.new_ver}"""
                    echo "********Pushing Docker Image With The Tag 'latest'********"
                    sh """docker push $DOCKERHUB_CREDENTIALS_USR/$User_Name-$App_Name-img:latest"""
                    def imagePushed = sh(script: """docker images -q $DOCKERHUB_CREDENTIALS_USR/$User_Name-$App_Name-img:latest""", returnStdout: true).trim()
                    if (imagePushed) {
                        echo '*********Docker Image Push SUCCESSFUL*********'  
                        Docker_Push = true
                    } else {
                        error '*********Docker Image Push FAILED*********'
                    } 
                }
            }
            post{
                success{
                    script{
                        echo '########## Docker Push is SUCCESSFUL ##########'
                        echo '########## Removing the Docker Images from the Local Machine ##########'
                        def latestImageExists1 = sh(script: """docker images -q $DOCKERHUB_CREDENTIALS_USR/$User_Name-$App_Name-img:latest""", returnStdout: true).trim()

                        if (latestImageExists1) {
                            echo 'Docker Image with the latest tag already exists. Removing the existing image...'
                            sh """docker rmi -f $DOCKERHUB_CREDENTIALS_USR/$User_Name-$App_Name-img:latest"""
                        } else {
                            echo 'Docker Image with the latest tag does not exist...'
                        }

                        def versionedImageExists1 = sh(script: """docker images -q $DOCKERHUB_CREDENTIALS_USR/$User_Name-$App_Name-img:${params.new_ver}""", returnStdout: true).trim()

                        if (versionedImageExists1) {
                            echo 'Docker Image with the specified version already exists. Removing the existing image...'
                            sh """docker rmi -f $DOCKERHUB_CREDENTIALS_USR/$User_Name-$App_Name-img:${params.new_ver}"""
                        } else {
                            echo 'Docker Image with the specified version does not exist...'
                        }
                        sh 'docker logout'
                    }
                }
                failure{
                    script{
                        echo '########## Docker Push is FAILED ##########'
                    }
                }
            }
        }
        stage ('Deploy_Main'){
            agent {
                label 'AC'
            }
            when{
                expression { Docker_Push == true }
            }
            steps {
                echo 'Deploying the Docker Image to the Production Environment'
                script {
                    echo '*********Deploying the Docker Image to the Production Environment*********'
                    // Copy the *.yaml file to the Main Server using scp command
                    sh """ scp $User_Name@$Build01_Pvt_IP:$BN01_Path/*.yaml $User_Name@$KubeM_Pvt_IP:/home/$User_Name/ """
                    echo "**********Copyed yaml file to Main Server**********"
                    // checking the pod status in the Main Server, if any pod name contains the User_Name and App_Name, then update the deployment, else create a new deployment
                    def podExists = sh(script: """ ssh $User_Name@$KubeM_Pvt_IP kubectl get pods -n default --no-headers | grep -E ".*${USER_NAME}.*${APP_NAME}.*|.*${APP_NAME}.*${USER_NAME}.*" || true """,returnStdout: true
                    ).trim()
                    if (podExists) {
                        echo '**********Pod is already running, So Updating the Deployment**********'
                        sh """ssh $User_Name@$KubeM_Pvt_IP kubectl apply -f royalhoteldeploy.yaml"""
                        sleep 5
                        sh """ssh $User_Name@$KubeM_Pvt_IP kubectl set image deploy $Deploy_Name $Container_Name=$DOCKERHUB_CREDENTIALS_USR/$User_Name-$App_Name-img:${params.new_ver}"""
                        sleep 15
                        sh """ssh $User_Name@$KubeM_Pvt_IP kubectl get pods -o wide"""
                    } else {
                        echo '**********Pod is not running, So Creating the Deployment**********'
                        sh """ssh $User_Name@$KubeM_Pvt_IP kubectl apply -f royalhoteldeploy.yaml"""
                        sleep 5
                        sh """ssh $User_Name@$KubeM_Pvt_IP kubectl get pods -o wide"""
                    } 
                    // if the pod status is errimagepull or pending, then it should rollout undo the deployment
                    def podStatuses = sh(script: """ssh $User_Name@$KubeM_Pvt_IP kubectl get pods -n default --no-headers | grep -E ".*${USER_NAME}.*${APP_NAME}.*|.*${APP_NAME}.*${USER_NAME}.*" | awk '{print \$3}'""", returnStdout: true).trim().split('\n')
                    def rollback = false

                    for (status in podStatuses) {
                        echo "Pod status: ${status}"
                        if (status == 'ErrImagePull' || status == 'Pending') {
                            rollback = true
                            break
                        }
                    }
                    if (rollback) {
                        echo '**********Pod Status is ErrImagePull or Pending, So Rolling Back the Deployment**********'
                        sh """ssh $User_Name@$KubeM_Pvt_IP kubectl rollout undo deployment/${APP_NAME}"""
                        sleep 15
                        sh """ssh $User_Name@$KubeM_Pvt_IP kubectl get pods -o wide"""
                    } else {
                        echo '**********All Pod Statuses are Running, So Deployment is SUCCESSFUL**********'
                    }
                    echo "**********Check The Deploy in Main Server**********"
                    sleep 15
                    echo "http://${env.KubeW01_Pub_IP}:30019"
                    echo "http://${env.KubeW02_Pub_IP}:30019"
                    Deploy_Main = true
                }
            }
            post{
                success{
                    script{
                        echo '########## Deployment to the Production Environment is SUCCESSFUL ##########'
                        cleanWs()
                        deleteDir()
                    }
                }
                failure{
                    script{
                        echo '########## Deployment to the Production Environment is FAILED ##########'
                    }
                }
            }
        }
        stage ('Cleaning_Production_Workspace'){
            agent {
                label 'BN01'
            }
            when{
                expression { Deploy_Main == true }
            }
            steps{
              script{
                echo '**********Cleaning RoyalHotel Production workspace**********'
                cleanWs()
                deleteDir()
              }
            }
        }
    }
    post {
        success {
            echo '########## Pipeline Completed Successfully ##########'
        }
        failure {
            echo '########## Pipeline Completed with FAILURE ##########'
        }
    }
}
