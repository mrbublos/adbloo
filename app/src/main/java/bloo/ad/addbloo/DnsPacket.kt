package bloo.ad.addbloo

class DnsPacket(val raw: ByteArray, var id: Int, var qr: Byte, var opcode: Byte, var aa: Byte, var tc: Byte, var rd: Byte, var ra: Byte, var z: Byte, var rcCode: Byte, var qdCount: Int, var anCount: Int, var nsCount: Int, var arCount: Int, val rest: ByteArray, val protocol: Byte) {

    companion object {
        private const val PROTOCOL_OFFSET = 9
        private const val UDP_TYPE = 17
        private const val TCP_TYPE = 6

        fun fromArray(input: ByteArray): DnsPacket? {
            if (input[PROTOCOL_OFFSET] != UDP_TYPE.toByte() && input[PROTOCOL_OFFSET] != TCP_TYPE.toByte()) {
                "Received packet non TCP/UDP: ${input[PROTOCOL_OFFSET]} allowing".log()
                return null
            } // not TCP and not UDP

//            "Received packet size (${input.size}) \n${input.toPrettyHex()}".log()
            val data = input.sliceArray(28 until input.size)
            return DnsPacket(input,
                             byteArr2Int(data.sliceArray(0..1)),
                             data[2].bits(0, 1),
                             data[2].bits(1, 4),
                             data[2].bits(5, 1),
                             data[2].bits(6, 1),
                             data[2].bits(7, 1),
                             data[3].bits(0, 1), // rd
                             data[3].bits(1, 3),
                             data[3].bits(4, 4),
                             byteArr2Int(data.sliceArray(4..5)),
                             byteArr2Int(data.sliceArray(6..7)),
                             byteArr2Int(data.sliceArray(8..9)),
                             byteArr2Int(data.sliceArray(10..11)),
                             data.sliceArray(12 until data.size),
                             input[9]
                            )
        }

        private fun byteArr2Int(byteArray: ByteArray): Int {
            var result = 0
            byteArray.forEach {
                result = (result shl 8) + (it.toInt() and 0xFF)
            }
            return result
        }
    }

    val queries: MutableList<String> = mutableListOf()

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
//        "ANCount: $anCount\n".log()
//        "NSCount: $nsCount\n".log()
//        "ARCount: $arCount\n".log()
        "Queries: ${queries.joinToString(",") }\n".log()
        "Protocol: $protocol\n".log()
//        "Raw: ${raw.toPrettyHex()}\n".log()
    }
}