package org.example.utils

import groovy.json.JsonOutput

class GoogleChat {

    static def send(String webhookUrl, String message) {

        def payload = JsonOutput.toJson([
            text: message
        ])

        def conn = new URL(webhookUrl).openConnection()
        conn.setRequestMethod("POST")
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", "application/json")
        conn.outputStream.write(payload.getBytes("UTF-8"))

        return conn.inputStream.text
    }
}
