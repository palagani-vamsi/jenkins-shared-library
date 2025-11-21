def call(String status, String webhookUrl) {

    String result = currentBuild.currentResult   // FAILURE, SUCCESS, UNSTABLE, ABORTED
    String buildUrl = env.BUILD_URL

    // Get build trigger reason (user/build/scm/cron)
    def causes = currentBuild.rawBuild.getCauses()
    String triggeredBy = causes?.collect { it.getShortDescription() }.join(", ")

    // Extract failure reason (stacktrace summary)
    String reason = "N/A"
    try {
        def actions = currentBuild.rawBuild.getActions()
        actions.each { action ->
            if (action?.causes) {
                reason = action.causes.collect { it.getShortDescription() }.join(", ")
            }
        }
    } catch (err) {
        reason = "Could not fetch reason"
    }

    def message = """
*Jenkins Build ${result}*

Job: ${env.JOB_NAME}
Build Number: ${env.BUILD_NUMBER}
Triggered By: ${triggeredBy}

*Build Result:* ${result}
*Reason:* ${reason}

🔗 *Build URL:* ${buildUrl}
"""

    sendMessageToGoogleChat(webhookUrl, message)
}
