def call(String webhookCredId, String status) {

    // Who triggered the build
    def causes = currentBuild.getBuildCauses()
    def triggeredBy = causes.collect { it.shortDescription }.join(', ')

    // Extract reason
    String reason = "No issues detected."

    if (status == "FAILURE") {
        def log = currentBuild.rawBuild?.getLog(500) ?: []
        def index = log.findIndexOf { it =~ /(ERROR|Exception|Failed|Caused by)/ }

        if (index > 0) {
            int start = Math.max(0, index - 5)
            int end = Math.min(log.size(), index + 15)
            reason = log[start..end].join("\n")
        }
    }

    if (status == "UNSTABLE") {
        reason = "Build unstable due to test failures or warnings."
    }

    if (status == "NOT_BUILT") {
        reason = "Build skipped due to unmet stage conditions."
    }

    def buildUrl = env.RUN_DISPLAY_URL ?: env.BUILD_URL

    def msg = """
Jenkins Build *${status}*

Job: *${env.JOB_NAME}*
Build Number: *${env.BUILD_NUMBER}*
Triggered By: *${triggeredBy}*

*Result:* ${status}
*Reason:* ${reason}

🔗 ${buildUrl}
"""

    withCredentials([string(credentialsId: webhookCredId, variable: 'CHAT_URL')]) {
        sh """
            curl -X POST "\$CHAT_URL" \
            -H "Content-Type: application/json" \
            -d '{ "text": "${msg.replace('"','\\"').replace("\n","\\n")}" }'
        """
    }
}
