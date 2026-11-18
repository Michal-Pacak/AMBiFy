package pl.ambif.ambify.server.lists

import org.jetbrains.exposed.v1.core.Table

// Mirrors of the tables created by V008__lists.sql and V009__lists_more.sql (the SQL file is the source of truth).

object ListRevs : Table("list_revs") {
    val organizationId = text("organization_id")
    val rev = long("rev")
    override val primaryKey = PrimaryKey(organizationId)
}

object ListsTable : Table("lists") {
    val id = text("id")
    val organizationId = text("organization_id")
    val type = text("type")
    val name = text("name")
    val everyone = bool("everyone")
    val createdBy = text("created_by")
    val createdAt = long("created_at")
    val archived = bool("archived")
    val deleted = bool("deleted")
    val rev = long("rev")
    val style = text("style").nullable()
    val access = text("access")
    override val primaryKey = PrimaryKey(id)
}

object ListMembers : Table("list_members") {
    val listId = text("list_id")
    val userId = text("user_id")
    override val primaryKey = PrimaryKey(listId, userId)
}

object ListItems : Table("list_items") {
    val id = text("id")
    val listId = text("list_id")
    val organizationId = text("organization_id")
    val values = text("item_values")
    val fieldRevs = text("field_revs")
    val checked = bool("checked")
    val checkedBy = text("checked_by").nullable()
    val checkedAt = long("checked_at").nullable()
    val addedBy = text("added_by")
    val addedAt = long("added_at")
    val changedBy = text("changed_by").nullable()
    val changedAt = long("changed_at").nullable()
    val cleared = bool("cleared")
    val pending = bool("pending")
    val lastDoneBy = text("last_done_by").nullable()
    val lastDoneAt = long("last_done_at").nullable()
    val deleted = bool("deleted")
    val rev = long("rev")
    val clearedAt = long("cleared_at").nullable()
    val clearedBy = text("cleared_by").nullable()
    val parentId = text("parent_id").nullable()
    override val primaryKey = PrimaryKey(id)
}

object ListConflicts : Table("list_conflicts") {
    val id = text("id")
    val organizationId = text("organization_id")
    val listId = text("list_id")
    val itemId = text("item_id")
    val field = text("field")
    val versions = text("versions")
    val createdAt = long("created_at")
    val resolved = bool("resolved")
    val resolvedBy = text("resolved_by").nullable()
    val resolvedAt = long("resolved_at").nullable()
    val resolution = text("resolution").nullable()
    val rev = long("rev")
    override val primaryKey = PrimaryKey(id)
}

object ListUnits : Table("list_units") {
    val organizationId = text("organization_id")
    val name = text("name")
    val forms = text("forms")
    val step = double("step")
    val packUnit = text("pack_unit").nullable()
    val rev = long("rev")
    override val primaryKey = PrimaryKey(organizationId, name)
}

object ListProducts : Table("list_products") {
    val organizationId = text("organization_id")
    val product = text("product")
    val unit = text("unit")
    val packSize = text("pack_size").nullable()
    val rev = long("rev")
    override val primaryKey = PrimaryKey(organizationId, product)
}

object ListChangesDone : Table("list_changes_done") {
    val id = text("id")
    val userId = text("user_id")
    val ok = bool("ok")
    val message = text("message").nullable()
    val queued = bool("queued")
    val createdAt = long("created_at")
    override val primaryKey = PrimaryKey(id)
}

object ListVariants : Table("list_variants") {
    val organizationId = text("organization_id")
    val product = text("product")
    val variant = text("variant")
    val usedAt = long("used_at")
    val rev = long("rev")
    override val primaryKey = PrimaryKey(organizationId, product, variant)
}
