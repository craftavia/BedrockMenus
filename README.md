# BedrockMenus

**Shows the chest menus of your other plugins to Bedrock players as native forms — no menu config needed.**

Bedrock players who join through Geyser get Java chest GUIs that are hard to use on a phone. BedrockMenus
catches those menus and shows a Bedrock form instead: one button per item, with the item's picture and a lore
line. A tap is passed back to the original menu as a normal click, so the plugin that opened the menu keeps
doing all the work. Java players see no difference.

**Download:** [Hangar](https://hangar.papermc.io/mrpunky/BedrockMenus) · [GitHub Releases](https://github.com/craftavia/BedrockMenus/releases)

## Features

- **Automatic** – works with menus from any plugin that uses a virtual chest inventory (shops, warps, kits,
  settings, …). Nothing to configure per menu.
- **Native look** – a `SimpleForm` with the item name, the amount and one lore line on every button.
- **Item pictures** – buttons show the item's Bedrock texture (1,600+ items mapped); player heads show the
  skin through a configurable image URL.
- **Right and shift clicks** – when an item's lore mentions right-click or shift-click, tapping it opens a
  detail screen with the full lore and one button per click type.
- **Safe clicks** – if the item changed while the form was open, the click is not sent; the player gets the
  current menu instead. Real chests, barrels and other block/entity inventories are never touched.
- **Chat buttons** – Bedrock players cannot click chat text. Clickable chat messages that run an
  accept/deny command (`/tpaccept`, `/party accept`, `/duel deny`, …) are also shown as a small form.
- **Per-player language** – form texts follow the player's client language. English and Turkish are bundled;
  add any language with a `messages_<code>.properties` file.
- **Proxy friendly** – a form left on screen from the previous server is closed after a server switch.
- No database, no metrics, no update checker; the server makes no outgoing connections.

## Requirements

| | |
|---|---|
| Server | Paper or a fork such as Purpur, **1.21.4 – 1.21.11 and 26.x** (use the latest build of your version) |
| Java | 21 or newer (26.x itself needs Java 25) |
| [PacketEvents](https://modrinth.com/plugin/packetevents) | **Required.** Built against 2.14.0, use a release that supports your Minecraft version |
| [Geyser](https://geysermc.org) + [Floodgate](https://geysermc.org/wiki/floodgate/) | Floodgate must be installed on the server that runs BedrockMenus (and on your proxy, as the Floodgate docs describe). Without Floodgate the plugin starts but stays idle, because it cannot tell who is a Bedrock player. |

Folia is not supported.

## Installation

1. Install PacketEvents and Floodgate on the server.
2. Download `BedrockMenus-x.y.z.jar` from [Releases](https://github.com/craftavia/BedrockMenus/releases)
   and put it in `plugins/`.
3. Start the server once and open `plugins/BedrockMenus/config.yml`.
4. Add every plugin whose menus need players to **put items in** to `menu.excluded-plugins` (see the notes
   below), then run `/bedrockmenus reload`.

## Commands and permissions

| Command | Description | Permission | Default |
|---|---|---|---|
| `/bedrockmenus reload` | Reloads `config.yml` and the message files | `bedrockmenus.admin` | op |
| `/bedrockmenus status` | Shows the version, Floodgate state, open form menus and mapped item icons | `bedrockmenus.admin` | op |
| `/bedrockmenus answer <id> <button\|close>` | Debug: answers a form printed to chat (see `debug` below) | `bedrockmenus.admin` | op |

## Configuration

```yaml
default-language: en

menu:
  enabled: true
  excluded-plugins: []      # never convert menus of these plugins
  only-plugins: []          # if set, convert ONLY these plugins' menus
  excluded-titles: []       # regular expressions on the plain menu title
  detail-screen: true       # right/shift click options for items that mention them
  click-words:
    right: ["right", "sağ", "sag tık", "sag tik"]
    shift: ["shift"]
    left: ["left", "sol ", "sol-"]
  item-taking: true         # give the item when the menu's plugin does not cancel the click
  important-line: ""        # e.g. "(price|cost)" to show the price line under the button
  button-images: true
  head-image-url: "https://mc-heads.net/head/{texture}/64"

close-stale-forms: true

chat-buttons:
  enabled: true
  commands:
    - "^/([a-z0-9_.-]+:)?(tpaccept|tpyes|tpdeny|tpno)( \\S+)?$"
    - "^/([a-z0-9_.-]+:)?(party|p|friend|friends|f|duel)( \\S+)? (accept|deny|decline)( \\S+){0,2}$"
  titles:
    tpaccept: teleport      # title text: chat-title.teleport in the messages files
    party: party
    duel: duel

debug:
  fake-bedrock-players: []
```

The full file with comments is created on first start. Form texts (buttons such as *Close* and *Back*,
click-type labels, chat form titles) are in `messages_en.properties` / `messages_tr.properties`.

**Head images.** `head-image-url` is not called by your server. It is sent to the Bedrock client, which
downloads the head picture itself. `{texture}` becomes the skin texture id (or the player name when there is
no texture). Point it at any service that returns a PNG for a texture id, or leave it empty for heads without
a picture.

**Debug.** Names in `debug.fake-bedrock-players` are Java players that are treated as Bedrock players: the
chest stays hidden and the form is printed to their chat, answered with `/bedrockmenus answer`. Use it to try
a menu without a Bedrock client, then empty the list again.

## Notes and limitations

- **Menus where players place items** (sell boxes, trade windows, backpacks, auction listing screens,
  crafting-like menus) cannot work as a form, because a form can only click. Add those plugins to
  `excluded-plugins` or the menu titles to `excluded-titles`; they then open as a normal chest.
- Only plugin-made virtual menus of the chest, barrel, hopper, dispenser, dropper and shulker box types are
  converted. Real block and entity inventories, anvils, furnaces and other menu types open as usual.
- Supported clicks are left, right, shift-left and shift-right. Number keys, drop, double and middle clicks
  are not available on the form.
- A button shows the item name and one lore line. The full lore is shown on the detail screen, which only
  opens for items whose lore mentions right or shift clicks (see `click-words`).
- Pictures use the Bedrock client's own vanilla textures. Custom resource pack models are not shown, and an
  item whose texture is unknown simply gets a button without a picture.
- The plugin that opened a menu is detected from the inventory holder or the call stack. A menu opened
  through another plugin's API may be attributed to that plugin; use `excluded-titles` in that case.
- The plugin it comes from runs on Craftavia's Purpur 26.2/26.3 network (Velocity, Geyser + Floodgate,
  PacketEvents 2.13) with the menus used there. This release was started on Paper 1.21.4, Paper 26.2 and
  Purpur 26.3 with PacketEvents 2.14.0 and Floodgate before publishing; menus from other plugins and
  1.21.x servers with real Bedrock players were not tested.

Made by the Craftavia team, used on play.craftavia.net, https://craftavia.net

## Building

```bash
./gradlew build
```

The jar is written to `build/libs/`.

## Türkçe

**İndir:** [Hangar](https://hangar.papermc.io/mrpunky/BedrockMenus) · [GitHub Releases](https://github.com/craftavia/BedrockMenus/releases)

BedrockMenus, Geyser ile giren Bedrock (mobil, konsol, Windows) oyunculara diğer eklentilerin sandık
menülerini **Bedrock'un kendi formu** olarak gösterir. Her eşya resmiyle birlikte bir düğme olur; düğmeye
dokunmak menüye normal bir tıklama olarak iletilir, yani menüyü açan eklenti eskisi gibi çalışır. Java
oyuncular hiçbir fark görmez.

- Menü başına ayar gerekmez: market, warp, kit, ayar menüleri gibi sanal sandık menülerini otomatik çevirir.
- Düğmelerde eşyanın Bedrock dokusu, kafalarda skin resmi görünür.
- Açıklamasında sağ tık/shift geçen eşyada ayrıntı ekranı açılır.
- Sohbetteki tıklanır kabul/ret yazıları (`/tpaccept`, `/party accept`, `/duel deny` gibi) Bedrock oyuncuya
  form olarak da gelir.
- Form metinleri oyuncunun oyun diline göre gelir (`messages_tr.properties`).

Kurulum: sunucuya PacketEvents ve Floodgate'i kurun, jar'ı `plugins/` klasörüne atın ve sunucuyu bir kez
açın. Oyuncunun içine **eşya koyduğu** menüleri (satış kutusu, takas, sırt çantası gibi) olan eklentileri
`config.yml` içindeki `menu.excluded-plugins` listesine ekleyin ve `/bedrockmenus reload` yazın.

Craftavia ekibi tarafından geliştirildi, play.craftavia.net sunucusunda kullanılıyor: https://craftavia.net

## License

[MIT](LICENSE) © Craftavia
