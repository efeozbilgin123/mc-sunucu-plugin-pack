# Paper API doğrulaması (Paper main, 31 Ekim 2026 commit'i)

Hepsi Paper kaynağında bulundu:
- PlayerQuitEvent.getReason() -> QuitReason {DISCONNECTED, KICKED, TIMED_OUT, ERRONEOUS_STATE}
- AsyncChatEvent (io.papermc.paper.event.player), renderer(ChatRenderer); ChatRenderer.viewerUnaware(ViewerUnaware: render(Player, Component displayName, Component message))
- Player.openBook(ItemStack)
- Entity.setVisibleByDefault(boolean), Player.showEntity(Plugin, Entity)
- LeashHitch; LivingEntity.setLeashHolder(Entity)
- Skull.setOwningPlayer(OfflinePlayer)
- AsyncPlayerPreLoginEvent.disallow(Result, Component), Result.KICK_BANNED
- HumanEntity.getEnderChest(); Inventory.getLocation()
- ArmorStand.setMarker(boolean)
- Bukkit.isStopping()
- InventoryOpenEvent, InventoryCloseEvent, BlockExplodeEvent, BlockPistonExtendEvent, TNTPrimeEvent(getPrimingEntity), PlayerBucketEmptyEvent, BlockIgniteEvent(getPlayer)
- Sesler: ENTITY_WARDEN_HEARTBEAT, BLOCK_BELL_USE, BLOCK_NOTE_BLOCK_BASEDRUM, BLOCK_CHAIN_PLACE, BLOCK_IRON_TRAPDOOR_CLOSE, BLOCK_BUBBLE_COLUMN_UPWARDS_AMBIENT
- Parçacıklar: SOUL, CAMPFIRE_COSY_SMOKE, LARGE_SMOKE, CRIT, CLOUD, END_ROD
- PotionEffectType.DARKNESS
- plugin.yml `libraries` (PluginDescriptionFile)

sqlite-jdbc: GitHub etiketleri 3.53.2.1, 3.53.4.0, 3.54.1.0. Seçilen: 3.53.4.0 (Maven Central'a erişilemiyor; bir önceki kararlı sürüm yayımlanmış olma ihtimali daha yüksek).
