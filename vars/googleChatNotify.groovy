def call(String webhookCredId, String status) {

    // -----------------------------
    // Who triggered the build?
    // -----------------------------
    def causes = currentBuild.getBuildCauses()
    def triggeredBy = causes.collect { it.shortDescription }.join(', ')

    // -----------------------------
    // Default Reason
    // -----------------------------
    String reason = "No issues detected."

    // -----------------------------
    // Extract Log & Find Error Reason
    // -----------------------------
    if (status == "FAILURE") {

        def log = currentBuild.rawBuild?.getLog(500) ?: []

        // Look for first matching failure line
        int idx = log.findIndexOf { line ->
            line =~ /(ERROR|Error|error|FAILURE|Failed|Exception|Traceback|Caused by)/
        }

        if (idx > 0) {
            int start = Math.max(0, idx - 10)
            int end   = Math.min(log.size() - 1, idx + 20)

            reason = log.subList(start, end).join("\n")
        } else {
            reason = "Failure occurred but no identifiable error pattern found in logs."
        }
    }

    if (status == "UNSTABLE") {
        reason = "Build unstable due to test failures or warnings."
    }

    if (status == "NOT_BUILT") {
        reason = "Build skipped due to conditional logic."
    }

    // -----------------------------
    // Build URL
    // -----------------------------
    def buildUrl = env.RUN_DISPLAY_URL ?: env.BUILD_URL

    // -----------------------------
    // LOG SIZE (for all non-success builds)
    // -----------------------------
    int logSize = 0
    if (status != "SUCCESS") {
        def logLines = currentBuild.rawBuild?.getLog(10000) ?: []
        logSize = logLines.size()
    }

    // -----------------------------
    // Final Notification Message
    // -----------------------------
    def msg = """
Jenkins Build *${status}*

Job: *${env.JOB_NAME}*
Build Number: *${env.BUILD_NUMBER}*
Triggered By: *${triggeredBy}*

*Result:* ${status}
*Log Size:* ${logSize} lines
*Reason:* 
${reason}

🔗 ${buildUrl}
"""

    // -----------------------------
    // Send to Google Chat
    // -----------------------------
    withCredentials([string(credentialsId: webhookCredId, variable: 'CHAT_URL')]) {
        sh """
            curl -X POST "\$CHAT_URL" \
            -H "Content-Type: application/json" \
            -d '{ "text": "${msg.replace('"','\\"').replace("\n","\\n")}" }'
        """
    }

}
