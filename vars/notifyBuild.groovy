def call(String status, String recipientEmail) {
    def subject = "Jenkins build ${status.toUpperCase()}: ${env.JOB_NAME} #${env.BUILD_NUMBER}"
    def body = """
Hello Team,

Jenkins build status: ${status}

Job: ${env.JOB_NAME}
Build Number: ${env.BUILD_NUMBER}
Build URL: ${env.BUILD_URL}
Status: ${currentBuild.currentResult}

This notification was sent to: ${recipientEmail}.
"""
    mail to: recipientEmail, subject: subject, body: body
}
