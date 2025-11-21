def call(String webhookUrl, String status) {

    // Who triggered?
    def causes = currentBuild.getBuildCauses()
    String triggeredBy = causes.collect { it.shortDescription }.join(', ')

    // Extract error lines (before 5, after 15)
    String errorContext = "No error detected"

    if (status == "FAILURE") {
        def log = currentBuild.rawBuild?.getLog(500) ?: []

        def index = log.findIndexOf { line ->
            line =~ /(ERROR|Exception|Failed|Caused by)/
        }

        if (index > 0) {
            int start = Math.max(0, index - 5)
            int end = Math.min(log.size(), index + 15)
            errorContext = log[start..end].join("\n")
        }
    }

    def buildUrl = env.RUN_DISPLAY_URL ?: env.BUILD_URL

    def message = """
Jenkins Build *${status}*

Job: *${env.JOB_NAME}*
Build Number: *${env.BUILD_NUMBER}*
Triggered By: *${triggeredBy}*

*Build Result:* ${status}
*Reason:* ${status == 'SUCCESS' ? 'Build completed successfully' : errorContext}

🔗 Build URL: ${buildUrl}
"""

    // ---- Google Chat message sender (sandbox safe) ----
    def json = """{"text": "${message.replace('"','\\"').replace("\n","\\n")}"}"""

    withCredentials([string(credentialsId: webhookUrl, variable: 'CHAT_URL')]) {
        sh """
            curl -X POST \$CHAT_URL \
            -H "Content-Type: application/json" \
            -d '${json}'
        """
    }
}
