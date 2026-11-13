package com.example.toolbox.ledger

data class LwwResult<T>(
    val items: List<T>,
    val pulledCount: Int,
    val pushedCount: Int,
)

/**
 * Last-Write-Wins by [id]: the item with the higher [updatedAt] wins,
 * ties go to remote. Used by ledger JSON/WebDAV merge and TOTP vault merge.
 */
object LastWriteWins {
    fun <T> merge(
        local: List<T>,
        remote: List<T>,
        id: (T) -> String,
        updatedAt: (T) -> Long,
        finish: (T) -> T = { it },
        sort: (List<T>) -> List<T> = { items -> items.sortedBy(updatedAt) },
    ): LwwResult<T> {
        val localById = local.associateBy(id)
        val remoteById = remote.associateBy(id)
        var pulled = 0
        var pushed = 0
        val merged = (localById.keys + remoteById.keys).map { key ->
            val localItem = localById[key]
            val remoteItem = remoteById[key]
            val chosen = when {
                remoteItem == null -> {
                    pushed++
                    localItem!!
                }
                localItem == null -> {
                    pulled++
                    remoteItem
                }
                updatedAt(remoteItem) >= updatedAt(localItem) -> {
                    if (updatedAt(remoteItem) > updatedAt(localItem)) pulled++
                    remoteItem
                }
                else -> {
                    pushed++
                    localItem
                }
            }
            finish(chosen)
        }
        return LwwResult(items = sort(merged), pulledCount = pulled, pushedCount = pushed)
    }
}
