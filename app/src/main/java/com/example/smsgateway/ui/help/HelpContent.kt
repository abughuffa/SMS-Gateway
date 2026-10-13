package com.example.smsgateway.ui.help

/**
 * Static copy for the Help screen. Written for a non-technical operator
 * who has just installed the app and needs to know:
 *   • what this app does
 *   • how to expose the phone as an SMS gateway
 *   • how to configure the server to talk to it
 *   • what to do when messages don't arrive
 */
object HelpContent {

    fun intro(): String =
        """
        SMS Gateway turns this Android phone into an SMS relay for your
        server. Inbound messages are pushed to your webhook and can also
        be pulled via HTTP. Outbound messages are sent through the phone's
        SIM card on request from your server.

        This screen explains the moving parts and how to troubleshoot.
        """.trimIndent()

    fun footer(versionName: String): String =
        "SMS Gateway $versionName — see the server's SMS Gateway Settings page for connection details."

    fun sections(): List<HelpSection> = listOf(

        HelpSection(
            title = "1. How it works",
            body = """
            Two directions, two paths:

            INBOUND (phone → server)
            • When a new SMS arrives, the app stores it in a local SQLite
              database (the "inbox").
            • It immediately POSTs a JSON payload to the webhook URL you
              configured. This is the "push" path — low latency.
            • The server also polls GET /inbox periodically. This is the
              "pull" path — used when the push fails, or as a safety net.
            • The two paths are idempotent: whichever arrives second is
              recognised as a duplicate and ignored.

            OUTBOUND (server → phone)
            • The server POSTs to /send with the recipient number, body,
              and (optionally) which SIM slot to use.
            • The app splits long messages, sends each part, and reports
              success/failure per part back to the server.
            """.trimIndent()
        ),

        HelpSection(
            title = "2. First-time setup",
            body = """
            1. Make sure at least one SIM card is inserted and has signal.
               The status bar at the top of the app shows the SIM state.

            2. Note this phone's LAN IP address. From a terminal on your
               server machine, run:

                   hostname -I

               The phone and the server must be on the same network (Wi-Fi
               or USB-adb-forward).

            3. Open the app's main screen and note the port the gateway
               listens on. Default is 8080 in WiFi mode, or an ADB-forwarded
               port in USB mode.

            4. On the server side, open SMS Gateway Settings and enter:

                 Base URL     http://<phone-ip>:<port>
                 API key      (leave blank for now)
                 Webhook URL  http://<server-ip>:<server-port>/api/sms/webhook

            5. Tap "Save" in the server UI. It will call POST /webhook on
               the phone, telling the phone where to push inbound messages.

            6. Send a test SMS to the phone's SIM. It should appear in the
               server's SMS Messages list within a few seconds.
            """.trimIndent()
        ),

        HelpSection(
            title = "3. Multipart (long) messages",
            body = """
            The SMS standard splits messages longer than 160 characters
            (70 for non-Latin scripts) into multiple "parts" carried in
            the same logical message.

            This app reads the concatenation header (UDH) from each part
            and forwards it to the server along with the message. The
            server then reassembles the parts into a single row.

            You do NOT need to do anything to enable this — it works
            automatically. If a long message shows up as several rows
            in the server's SMS Messages list, the multipart fields
            didn't reach the server. See "Troubleshooting" below.

            Important: reassembly depends on every part arriving. If a
            part is lost in transit (rare, but happens on weak signal),
            the group can't be assembled and the server will discard
            the orphan parts after a configurable window.
            """.trimIndent()
        ),

        HelpSection(
            title = "4. WiFi vs. USB mode",
            body = """
            WIFI MODE
            • The phone runs an HTTP server on its LAN IP.
            • Works as long as the phone and server are on the same network.
            • Fastest to set up; no cables.
            • Trade-off: anyone on the LAN can reach the API unless you
              set an API key in the server's settings.

            USB MODE
            • You connect the phone via USB and run:

                  adb forward tcp:8080 tcp:8080

              on the server machine.
            • The server talks to http://127.0.0.1:8080, which ADB tunnels
              to the phone.
            • No network exposure — the phone's HTTP server binds only to
              loopback and is invisible to the LAN.
            • Recommended when security matters more than convenience.

            Switch modes from the app's main screen. The change takes
            effect immediately.
            """.trimIndent()
        ),

        HelpSection(
            title = "5. Security",
            body = """
            Two independent tokens protect the system:

            1. SERVER-SIDE API KEY
               Sent by the server as the X-Api-Key header on every request.
               Set it in the server's SMS Gateway Settings page; the phone
               rejects requests that don't carry the right value.

               Leave it blank for initial testing. Set it before exposing
               the phone to a shared Wi-Fi network.

            2. WEBHOOK SIGNATURE
               Every inbound webhook POST carries an X-Signature header
               computed as HMAC-SHA256(body, secret). The server verifies
               it before processing the payload.

               If the secret is empty on either side, the check is skipped.
               Set it on both the phone and the server, and make sure they
               match exactly — a single character difference will cause
               every webhook to be rejected with 401.

            Never expose the phone's HTTP server to the public internet.
            The API has no rate-limiting and no account system.
            """.trimIndent()
        ),

        HelpSection(
            title = "6. Troubleshooting",
            body = """
            MESSAGES ARE NOT ARRIVING AT THE SERVER

            • Check that the phone has signal and that SMS permissions
              are granted. Android Settings → Apps → SMS Gateway →
              Permissions → SMS must be "Allow".

            • Open the app's log screen. Every received SMS prints a
              "Stored inbound #NN" line. If you see that but no webhook
              call, the webhook URL is wrong.

            • Test the webhook URL from a browser on the phone:

                  http://<server-ip>:<port>/api/sms/webhook

              A 404 means the path is wrong (check for the /api prefix).
              A 401 means the HMAC signature didn't match.
              A connection error means the server isn't reachable.

            MESSAGES ARE DUPLICATED

            • Both the webhook and the poller delivered the same message.
              This is expected on the network, but the server deduplicates
              by gateway ID. If you see true duplicates, the server's
              uniqueness index is missing or the cursor was reset.

            LONG MESSAGES SHOW AS MULTIPLE ROWS

            • The server isn't seeing the multipart fields. Add a
              temporary log in the server's webhook endpoint that prints
              the raw request body. If the JSON contains "refNumber",
              "partNumber", and "totalParts", the server's deserializer
              is dropping them. If not, the phone didn't extract the UDH.

            APP CRASHES ON FIRST SMS

            • This is a known issue on some devices. Clear the app's data
              (Android Settings → Apps → SMS Gateway → Storage → Clear
              data) and reinstall.

            OUTBOUND MESSAGES STAY "QUEUED"

            • Check the SIM is not PIN-locked. The gateway app cannot
              unlock a SIM; unlock it in Android's SIM settings.

            • Check the recipient number includes a country code if the
              destination is in a different country.
            """.trimIndent()
        ),

        HelpSection(
            title = "7. Configuration reference",
            body = """
            The app stores these settings locally:

              webhook.url      Where to POST inbound SMS. Set by the
                               server's /webhook endpoint or via
                               the app's Settings screen.
              webhook.secret   Shared secret for HMAC-SHA256 signing.
              stat.received    Running total of inbound messages.
              stat.sent        Running total of outbound messages.
              stat.sent_failed Running total of outbound failures.

            The phone's HTTP API surface (used by the server):

              GET    /status           Liveness, uptime, SIM state, counts
              POST   /send             Send an outbound SMS
              GET    /inbox            Pull inbound SMS since a cursor
              POST   /webhook          Register the webhook URL + secret
              DELETE /webhook          Clear the webhook

            Every endpoint except /status requires the X-Api-Key header
            if an API key is configured.
            """.trimIndent()
        )
    )
}