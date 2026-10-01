package com.memochat.app.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update

@Dao
interface EntryDao {
    @Query("SELECT * FROM entries WHERE hidden = 0 AND deletedAt = 0 ORDER BY pinned DESC, updatedAt DESC, id DESC")
    suspend fun getAll(): List<Entry>

    @Query("SELECT * FROM entries WHERE id = :id")
    suspend fun getById(id: Long): Entry?

    @Insert
    suspend fun insert(entry: Entry): Long

    @Update
    suspend fun update(entry: Entry)

    @Delete
    suspend fun delete(entry: Entry)

    @Query("DELETE FROM entries WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM entries")
    suspend fun deleteAll()

    @Query("UPDATE entries SET pinned = :pinned WHERE id = :id")
    suspend fun setPinned(id: Long, pinned: Boolean)

    @Query("UPDATE entries SET hidden = :hidden WHERE id = :id")
    suspend fun setHidden(id: Long, hidden: Boolean)

    @Query("UPDATE entries SET deletedAt = :ts WHERE id = :id")
    suspend fun softDelete(id: Long, ts: Long)

    @Query("UPDATE entries SET deletedAt = 0 WHERE id = :id")
    suspend fun restore(id: Long)

    @Query("SELECT * FROM entries WHERE deletedAt > 0 ORDER BY deletedAt DESC")
    suspend fun trashEntries(): List<Entry>
}

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE entryId = :entryId AND deletedAt = 0 ORDER BY createdAt ASC, id ASC")
    suspend fun getForEntry(entryId: Long): List<Message>

    @Query("SELECT * FROM messages")
    suspend fun getAll(): List<Message>

    @Insert
    suspend fun insert(message: Message): Long

    @Delete
    suspend fun delete(message: Message)

    @Query("DELETE FROM messages")
    suspend fun deleteAll()

    @Query("UPDATE messages SET deletedAt = :ts WHERE id = :id")
    suspend fun softDelete(id: Long, ts: Long)

    @Query("UPDATE messages SET deletedAt = 0 WHERE id = :id")
    suspend fun restore(id: Long)

    @Query("SELECT * FROM messages WHERE deletedAt > 0 ORDER BY deletedAt DESC")
    suspend fun trashMessages(): List<Message>

    /** 用于会话列表的最近一条预览 */
    @Query("SELECT content FROM messages WHERE entryId = :entryId ORDER BY createdAt DESC, id DESC LIMIT 1")
    suspend fun lastContent(entryId: Long): String?

    @Query("SELECT type FROM messages WHERE entryId = :entryId ORDER BY createdAt DESC, id DESC LIMIT 1")
    suspend fun lastType(entryId: Long): Int?

    @Query("SELECT DISTINCT entryId FROM messages WHERE content LIKE '%' || :kw || '%'")
    suspend fun searchEntryIds(kw: String): List<Long>

    @Query("SELECT content FROM messages WHERE entryId = :entryId AND content LIKE '%' || :kw || '%' ORDER BY createdAt DESC, id DESC LIMIT 1")
    suspend fun searchSnippet(entryId: Long, kw: String): String?

    @Query("SELECT COUNT(*) FROM messages WHERE entryId = :entryId AND content LIKE '%' || :kw || '%'")
    suspend fun searchCount(entryId: Long, kw: String): Int

    @Query("SELECT id FROM messages WHERE entryId = :entryId AND content LIKE '%' || :kw || '%' ORDER BY createdAt ASC, id ASC LIMIT 1")
    suspend fun firstMatchId(entryId: Long, kw: String): Long?


    @Query("UPDATE messages SET content = :content, edited = 1 WHERE id = :id")
    suspend fun updateContent(id: Long, content: String)

    @Query("UPDATE messages SET starred = :s WHERE id = :id")
    suspend fun setStarred(id: Long, s: Boolean)

    @Query("SELECT * FROM messages WHERE starred = 1 ORDER BY createdAt DESC")
    fun starredAll(): kotlinx.coroutines.flow.Flow<List<Message>>

    @Query("UPDATE messages SET isBackfill = :b WHERE id = :id")
    suspend fun setBackfill(id: Long, b: Boolean)

    @Query("DELETE FROM messages WHERE entryId = :entryId")
    suspend fun deleteForEntry(entryId: Long)

    @Query("DELETE FROM messages WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)
}

@Dao
interface DayMetaDao {
    @Query("SELECT * FROM day_meta")
    suspend fun all(): List<DayMeta>

    @Query("SELECT * FROM day_meta WHERE dayKey = :dayKey")
    suspend fun get(dayKey: String): DayMeta?

    @Insert(onConflict = androidx.room.OnConflictStrategy.REPLACE)
    suspend fun upsert(meta: DayMeta)
}

@Dao
interface CapsuleDao {
    @Query("SELECT * FROM capsules ORDER BY unlockAt ASC")
    suspend fun all(): List<TimeCapsule>

    @Insert
    suspend fun insert(c: TimeCapsule): Long

    @Delete
    suspend fun delete(c: TimeCapsule)

    @Query("SELECT COUNT(*) FROM capsules WHERE unlockAt <= :now AND unlocked = 0")
    suspend fun lockedButDue(now: Long): Int

    @Query("UPDATE capsules SET unlocked = 1 WHERE id = :id")
    suspend fun markUnlocked(id: Long)
}
