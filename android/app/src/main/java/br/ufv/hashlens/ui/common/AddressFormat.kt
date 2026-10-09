package br.ufv.hashlens.ui.common

/** Endereço abreviado para a camada principal (ux.md): `0x12ab…9f3c`. O completo fica nos detalhes técnicos. */
object AddressFormat {
    private const val HEAD = 6
    private const val TAIL = 4

    fun short(address: String): String =
        if (address.length <= HEAD + TAIL) address else "${address.take(HEAD)}…${address.takeLast(TAIL)}"
}
