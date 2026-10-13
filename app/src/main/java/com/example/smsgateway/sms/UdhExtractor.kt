
package com.example.smsgateway.sms

import com.example.smsgateway.log.Log

data class UdhInfo(val concatRef: Int, val partNumber: Int, val totalParts: Int)

/**
 * Extracts UDH concatenation metadata from a raw SMS-DELIVER PDU.
 *
 * Two layouts are tried, in order:
 *
 *   1. Standard — the PDU begins with the SMSC prefix:
 *        [0]      SMSC length in bytes (0..12 typically)
 *        [1]      SMSC type-of-address
 *        [2..n]   SMSC address
 *        [n+1]    PDU-type byte (bit 6 = UDH present)
 *        ...
 *
 *   2. Stripped — some OEM ROMs (Xiaomi/MIUI, some Samsung) remove the
 *      SMSC prefix before dispatching SMS_RECEIVED. The PDU then starts
 *      directly at the PDU-type byte:
 *        [0]      PDU-type byte (bit 6 = UDH present)
 *        [1]      sender address length (semi-octets)
 *        ...
 *
 * After the PDU-type byte, both layouts are identical:
 *   TP-OA (1 + 1 + ceil(len/2) bytes)
 *   TP-PID (1) + TP-DCS (1) + TP-SCTS (7)
 *   TP-UDL (1)
 *   UDH (1 + udhLen bytes)
 */
object UdhExtractor {

    private const val TAG = "Udh"

    // PDU-type bit 6 (0x40) indicates a UDH is present.
    private const val UDH_PRESENT_MASK = 0x40

    // IEIs for concatenated short messages.
    private const val IEI_CONCAT_8BIT  = 0x00
    private const val IEI_CONCAT_16BIT = 0x08

    /**
     * @param pdu raw SMS-DELIVER PDU bytes from intent.extras.get("pdus")
     * @return UdhInfo for multipart, null for single-part or unparseable
     */
    fun extract(pdu: ByteArray): UdhInfo? {
        if (pdu.size < 10) return null

        // Attempt 1: standard layout (with SMSC prefix).
        extractInternal(pdu, withSmsc = true)?.let {
            Log.i(TAG, "UDH found using standard-SMSC layout (PDU size=${pdu.size})")
            return it
        }

        // Attempt 2: stripped-SMSC layout (some OEM ROMs).
        extractInternal(pdu, withSmsc = false)?.let {
            Log.i(TAG, "UDH found using stripped-SMSC layout (PDU size=${pdu.size})")
            return it
        }

        // Neither layout worked. Log a hex dump for diagnosis — this is
        // the single most useful piece of data when debugging a new device.
        Log.w(TAG, "UDH parse failed. PDU[${pdu.size}]=${toHex(pdu)}")
        return null
    }

    // ─────────────────────────────────────────────────────────────────────

    private fun extractInternal(pdu: ByteArray, withSmsc: Boolean): UdhInfo? {
        return try {
            var idx = 0

            // (1) SMSC prefix — only present in the standard layout.
            if (withSmsc) {
                val smscLen = pdu[idx].toInt() and 0xFF
                idx = 1 + smscLen
                if (idx >= pdu.size) return null
            }

            // (2) PDU-type byte — check bit 6 for UDH presence.
            val pduType = pdu[idx].toInt() and 0xFF
            idx += 1

            // SMS-DELIVER MTI (bits 0-1) must be 00. Anything else means we
            // parsed the wrong byte and should bail out of this layout.
            if ((pduType and 0x03) != 0x00) return null

            if ((pduType and UDH_PRESENT_MASK) == 0) return null

            // (3) Sender address — length (semi-octets) + ToA + ceil(len/2).
            if (idx >= pdu.size) return null
            val addrLen = pdu[idx].toInt() and 0xFF
            idx += 1 + 1 + (addrLen + 1) / 2
            if (idx >= pdu.size) return null

            // (4) TP-PID (1) + TP-DCS (1) + TP-SCTS (7).
            idx += 1 + 1 + 7
            if (idx >= pdu.size) return null

            // (5) TP-UDL — skip; we don't need the length value.
            idx += 1
            if (idx >= pdu.size) return null

            // (6) UDH length byte.
            val udhLen = pdu[idx].toInt() and 0xFF
            idx += 1
            if (udhLen == 0 || idx + udhLen > pdu.size) return null

            // (7) Walk the UDH information elements.
            val end = idx + udhLen
            while (idx + 1 < end) {
                val iei    = pdu[idx].toInt() and 0xFF
                val ieLen  = pdu[idx + 1].toInt() and 0xFF
                idx += 2

                if (idx + ieLen > end) return null   // malformed

                when {
                    iei == IEI_CONCAT_8BIT && ieLen == 3 -> {
                        val ref = pdu[idx].toInt() and 0xFF
                        val tot = pdu[idx + 1].toInt() and 0xFF
                        val seq = pdu[idx + 2].toInt() and 0xFF
                        if (tot > 1 && seq in 1..tot) {
                            return UdhInfo(ref, seq, tot)
                        }
                    }

                    iei == IEI_CONCAT_16BIT && ieLen == 4 -> {
                        val ref = ((pdu[idx].toInt() and 0xFF) shl 8) or
                                (pdu[idx + 1].toInt() and 0xFF)
                        val tot = pdu[idx + 2].toInt() and 0xFF
                        val seq = pdu[idx + 3].toInt() and 0xFF
                        if (tot > 1 && seq in 1..tot) {
                            return UdhInfo(ref, seq, tot)
                        }
                    }
                }

                idx += ieLen
            }

            null
        } catch (t: Throwable) {
            null   // try the other layout
        }
    }

    private fun toHex(b: ByteArray): String =
        b.joinToString("") { "%02X".format(it.toInt() and 0xFF) }
}



////
////object UdhExtractor {
////
////    private const val TAG = "Udh"
////
////    private val getUdhMethod by lazy {
////        try {
////            SmsMessage::class.java
////                .getDeclaredMethod("getUserDataHeader")
////                .apply { isAccessible = true }
////        } catch (t: Throwable) {
////            Log.w(TAG, "getUserDataHeader not available: ${t.message}")
////            null
////        }
////    }
////
////    /** Returns null for single-part SMS or if UDH cannot be read. */
////    fun extract(sms: SmsMessage): UdhInfo? {
////        val m = getUdhMethod ?: return null
////        return try {
////            val header = m.invoke(sms) ?: return null
////            val concat = readField(header, "concatRef") ?: return null
////            val ref = (readField(concat, "refNumber") as? Number)?.toInt() ?: return null
////            val seq = (readField(concat, "seqNumber") as? Number)?.toInt() ?: return null
////            val cnt = (readField(concat, "msgCount")  as? Number)?.toInt() ?: return null
////            if (cnt <= 1) null else UdhInfo(ref, seq, cnt)
////        } catch (t: Throwable) {
////            Log.w(TAG, "UDH extraction failed: ${t.message}")
////            null
////        }
////    }
////
////    private fun readField(obj: Any, name: String): Any? =
////        try { obj.javaClass.getField(name).get(obj) } catch (_: Throwable) { null }
////}
//
//package com.example.smsgateway.sms
//
//import com.example.smsgateway.log.Log
//
//data class UdhInfo(val concatRef: Int, val partNumber: Int, val totalParts: Int)
//
///**
// * Extracts UDH concatenation metadata directly from a raw SMS-DELIVER PDU.
// *
// * This replaces the previous reflection-based approach, which fails on
// * Android 9+ because getUserDataHeader() is on the hidden-API block list.
// *
// * PDU layout (SMS-DELIVER):
// *   [0]      SMSC length
// *   [1]      SMSC type-of-address
// *   [2..n]   SMSC address (n = SMSC length)
// *   [n+1]    PDU type (bit 6 = UDH present)
// *   [n+2]    sender address length (in semi-octets)
// *   [n+3]    sender type-of-address
// *   [n+4..]  sender address (ceil(len/2) bytes)
// *   ...      TP-PID, TP-DCS, TP-SCTS (1 + 1 + 7 bytes)
// *   ...      TP-UDL (user data length)
// *   ...      UDH + message body
// */
//object UdhExtractor {
//
//    private const val TAG = "Udh"
//
//    // PDU type bit 6 (0x40) indicates a UDH is present.
//    private const val UDH_PRESENT_MASK = 0x40
//
//    // IEI for concatenated short messages.
//    private const val IEI_CONCAT_8BIT  = 0x00
//    private const val IEI_CONCAT_16BIT = 0x08
//
//    /**
//     * @param pdu raw SMS-DELIVER PDU bytes from intent.extras.get("pdus")
//     * @return UdhInfo for multipart, null for single-part or unparseable
//     */
//    fun extract(pdu: ByteArray): UdhInfo? {
//        return try {
//            var idx = 0
//
//            // (1) SMSC — length byte + type byte + address bytes
//            val smscLen = pdu[idx].toInt() and 0xFF
//            idx += 1 + smscLen
//            if (idx >= pdu.size) return null
//
//            // (2) PDU type — check bit 6 for UDH presence
//            val pduType = pdu[idx].toInt() and 0xFF
//            idx += 1
//            if ((pduType and UDH_PRESENT_MASK) == 0) return null
//
//            // (3) Sender address — length (semi-octets) + type + ceil(len/2) bytes
//            val addrLen = pdu[idx].toInt() and 0xFF
//            idx += 1 + 1 + (addrLen + 1) / 2
//            if (idx >= pdu.size) return null
//
//            // (4) TP-PID + TP-DCS + TP-SCTS = 1 + 1 + 7 bytes
//            idx += 1 + 1 + 7
//            if (idx >= pdu.size) return null
//
//            // (5) TP-UDL (we don't need the value, just skip it)
//            idx += 1
//            if (idx >= pdu.size) return null
//
//            // (6) Now at the start of user data. First byte is UDH length.
//            val udhLen = pdu[idx].toInt() and 0xFF
//            idx += 1
//            if (udhLen == 0 || idx + udhLen > pdu.size) return null
//
//            // (7) Walk the UDH IEs looking for concatenation IE
//            var end = idx + udhLen
//            while (idx + 1 < end) {
//                val iei  = pdu[idx].toInt() and 0xFF
//                val ieLen = pdu[idx + 1].toInt() and 0xFF
//                idx += 2
//
//                if (iei == IEI_CONCAT_8BIT && ieLen == 3) {
//                    val ref  = pdu[idx].toInt() and 0xFF
//                    val tot  = pdu[idx + 1].toInt() and 0xFF
//                    val seq  = pdu[idx + 2].toInt() and 0xFF
//                    if (tot > 1) return UdhInfo(ref, seq, tot)
//                } else if (iei == IEI_CONCAT_16BIT && ieLen == 4) {
//                    val ref  = ((pdu[idx].toInt() and 0xFF) shl 8) or
//                            (pdu[idx + 1].toInt() and 0xFF)
//                    val tot  = pdu[idx + 2].toInt() and 0xFF
//                    val seq  = pdu[idx + 3].toInt() and 0xFF
//                    if (tot > 1) return UdhInfo(ref, seq, tot)
//                }
//                idx += ieLen
//            }
//
//            null
//        } catch (t: Throwable) {
//            Log.w(TAG, "PDU UDH parse failed: ${t.message}")
//            null
//        }
//    }
//}