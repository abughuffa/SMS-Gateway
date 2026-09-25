package com.example.smsgateway.model

import kotlinx.serialization.Serializable

@Serializable
data class SendRequest(
    val to: String,
    val body: String,
    val simSlot: Int? = null,
    val reference: String? = null
)

@Serializable
data class SendResponse(
    val id: String,
    val status: String,          // "queued" | "sent" | "failed"
    val reference: String? = null,
    val error: String? = null
)

@Serializable
data class StatusResponse(
    val mode: String,
    val uptimeSec: Long,
    val simReady: Boolean,
    val sentCount: Int,
    val receivedCount: Int
)

@Serializable
data class InboxMessage(
    val id: Long,
    val from: String,
    val body: String,
    val ts: Long
)

@Serializable
data class InboxResponse(val messages: List<InboxMessage>)

@Serializable
data class WebhookRequest(
    val url: String,
    val secret: String? = null
)

@Serializable
data class ErrorResponse(
    val error: String,
    val detail: String? = null
)