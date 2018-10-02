package bloo.ad.addbloo

class DnsPacket(var raw: ByteArray, var id: Int, var qr: Byte, var opcode: Byte, var aa: Byte,
                var tc: Byte, var rd: Byte, var ra: Byte, var z: Byte, var rcCode: Byte,
                var qdCount: Int, var anCount: Int, var nsCount: Int, var arCount: Int,
                val rest: ByteArray, var protocol: Byte, val datagram: ByteArray) {

    companion object {
        private const val PROTOCOL_OFFSET = 9
        private const val UDP_TYPE = 17
        private const val TCP_TYPE = 6

        fun fromArray(input: ByteArray): DnsPacket? {
            if (input[PROTOCOL_OFFSET] != UDP_TYPE.toByte() && input[PROTOCOL_OFFSET] != TCP_TYPE.toByte()) {
//                "Received packet non TCP/UDP: ${input[PROTOCOL_OFFSET]}, allowing".log()
                return null
            }

            val datagram = input.sliceArray(28 until input.size)
            val result = fromDatagram(datagram)
            result.raw = input
            result.protocol = input[PROTOCOL_OFFSET]
            return result
        }

        fun fromDatagram(datagram: ByteArray): DnsPacket {
            val raw = ByteArray(28) { _ -> 0.toByte() } + datagram
            return DnsPacket(raw, // full ip packet
                             datagram.sliceArray(0..1).toInt(),
                             datagram[2].bits(0, 1),
                             datagram[2].bits(1, 4),
                             datagram[2].bits(5, 1),
                             datagram[2].bits(6, 1),
                             datagram[2].bits(7, 1),
                             datagram[3].bits(0, 1), // rd
                             datagram[3].bits(1, 3), // ra
                             datagram[3].bits(4, 4), // z
                             datagram.sliceArray(4..5).toInt(), // rcCode
                             datagram.sliceArray(6..7).toInt(), // qdCount
                             datagram.sliceArray(8..9).toInt(), // anCount
                             datagram.sliceArray(10..11).toInt(),
                             datagram.sliceArray(12 until datagram.size),
                             UDP_TYPE.toByte(),
                             datagram)
        }
    }

    val queries: MutableList<String> = mutableListOf()
    val answers: MutableList<String> = mutableListOf()

    init {
        parseQuestions()
    }

    private fun parseQuestions() {
        var offset = 0
        val name = mutableListOf<String>()
        val refs = mutableMapOf<Int, String>()
        var currRef = 28 // TODO check if initial offset is ok
        while (true) {
            val strSize = rest[offset++]
            // octet
            if (strSize > 0) {
                name.add(String(rest.sliceArray(offset until (offset + strSize))))
                offset += strSize
            }

            // reference
            if (strSize < 0) { name.add(refs[rest[offset++].toInt()] ?: "") }

            // termination
            if (strSize == 0.toByte()) {
                val newName = name.joinToString(".")
                queries.add(newName)
                refs[currRef] = newName
                currRef = ++offset + 28
                name.clear()
            }

            if (queries.size == qdCount) { break }
        }
    }

    fun fillHeaders(request: DnsPacket) {
        fillIpHeader(request)
        fillUdpHeader(request)
    }

    private fun fillUdpHeader(request: DnsPacket) {
        raw[20] = request.raw[22] // source port
        raw[21] = request.raw[23]

        raw[22] = request.raw[20] // destination port
        raw[23] = request.raw[21]

        val length = 8 + datagram.size
        raw[24] = (length shr 8).toByte() // length
        raw[25] = length.rem(256).toByte()

        raw[26] = 0 // checksum (unused)
        raw[27] = 0
    }

    private fun fillIpHeader(request: DnsPacket) {
        raw[0] = 0x45.toByte() //ipv4

        raw[2] = (raw.size shr 8).toByte() // size
        raw[3] = raw.size.rem(256).toByte() // size

        raw[4] = 1.toByte() // identification
        raw[5] = 1.toByte() // identification

        raw[8] = 128.toByte() // TTL
        raw[9] = request.raw[9] // protocol

        raw[10] = 0.toByte() // checksum
        raw[11] = 0.toByte()

        raw[12] = request.raw[16] // source
        raw[13] = request.raw[17]
        raw[14] = request.raw[18]
        raw[15] = request.raw[19]

        raw[16] = request.raw[12] // destination
        raw[17] = request.raw[13]
        raw[18] = request.raw[14]
        raw[19] = request.raw[15]

        calculateChecksum()
    }

    fun makeLoopbackResponse() {
        // TODO implement
    }

    fun getLength() = raw.size

    private fun calculateChecksum() {
        // checksum
        val sum = raw.sliceArray(0..19).toInt()
        val carry = sum shr 16
        val value = sum - (carry shl 16) + carry
        val checksum = value xor 0xFFFF
        raw[10] = (checksum shr 8).toByte()
        raw[11] = checksum.rem(256).toByte()
    }

    private fun isTcpOrUdp(): Boolean {
        return protocol == TCP_TYPE.toByte() || protocol == UDP_TYPE.toByte()
    }

    fun log() {
        if (!isTcpOrUdp()) { return }

        "***DNS packet***\n".log()
        "Id: $id\n".log()
//        "QR: $qr\n".log()
        "Opcode: $opcode\n".log()
//        "AA: $aa\n".log()
//        "TC: $tc\n".log()
//        "RD: $rd\n".log()
//        "RA: $ra\n".log()
//        "RCode: $rcCode\n".log()
        "QDCount: $qdCount\n".log()
        "ANCount: $anCount\n".log()
//        "NSCount: $nsCount\n".log()
//        "ARCount: $arCount\n".log()
        "Queries: ${queries.joinToString(",") }\n".log()
        "Protocol: $protocol\n".log()
//        "Raw: ${raw.toPrettyHex()}\n".log()
    }
}