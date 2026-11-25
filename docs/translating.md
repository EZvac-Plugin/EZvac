# Translations

Since BETA 1.1, everything EZvac says to players can be in another language:
chat, menus, the setup, the tutorial, screens, signs and the HUD's words. One
language is used for the whole server.

## Choosing the language

In `plugins/EZvac/config.yml`:

```yaml
language: en
```

Restart the server after changing it. A language EZvac doesn't have yet is
said once in the log, and EZvac speaks English. A word a translation hasn't
reached yet is shown in English, so a half-finished translation is still
usable.

**English is the only language in the jar so far.** The others come as they
are translated (see below).

### What stays English

- **Commands and their words**, such as `/hvac wind set gale`, and tab
  completion. That way commands, scripts and these docs work on every server.
  The help (`/hvac help`) is translated and explains them.
- **The console and `config.yml`**, including its comments.
- **The HUD lines in `config.yml`.** They're yours to write in any language.
  The values they show, such as the wind, the season and the weather, follow
  the language.
- **These docs.**
- **The API.** Addons see the same names and values on every language. Word
  placeholders such as `%ezvac_season%` follow the language, so a scoreboard
  shows what players read.

## Changing single words

To change a few words without translating everything, make
`plugins/EZvac/lang/<language>.yml`, such as `plugins/EZvac/lang/en.yml`, with
only the keys you want to change:

```yaml
screen:
  ready: "Standing by"
  word:
    clear: "Sunny"
```

Every other word stays as it is. The keys are in
[`lang/en.yml`](https://github.com/EZvac-Plugin/EZvac/blob/main/ezvac-plugin/src/main/resources/lang/en.yml).
Your file survives updates. A key that a later release renames is simply
ignored.

The same file also works for a language EZvac doesn't ship: name it
`plugins/EZvac/lang/<code>.yml` and set `language: <code>`.

## Helping translate

Translations are welcome, and EZvac is translated on
**[Weblate](https://hosted.weblate.org/engage/ezvac/)**: sign in, pick or add your language and
translate in your browser, without touching files. Weblate sends the work to
us as a pull request; every one is checked and then merged, and the next
release carries it. Questions are welcome on our
[Discord](https://discord.gg/tCedQgFFDU).

You can also translate by file:
1. Copy `lang/en.yml` to `lang/<code>.yml`, using a short language code such
   as `de`, `fr`, `ru` or `pt_br`.
2. Translate the values.
3. Send it as a pull request.

### How the words work

Each value is [MiniMessage](https://docs.advntr.dev/minimessage/format.html):

```yaml
set: "<green>It is <day> in '<world>' until the day ends. /hvac wind set auto lets it blow as it would."
```

- **Keep every placeholder,** like `<day>` and `<world>`, spelled exactly as it
  is. You can move it anywhere in the sentence. A test checks that a
  translation has the same placeholders as English.
- **Colours** (`<green>`, `<gray>` and the like) can move with the words they
  colour. Only colour and decoration tags (`<bold>`, `<italic>`) work. Others,
  such as click or hover, show as typed, and the build rejects them.
- **Screens and signs are small.** Text too long for one is cut, never spilt
  over, so keep screen words (keys under `screen`, `station`, `wind`, `air`,
  `weather` and `hud`) about as short as English. Latin, Greek and Cyrillic letters are
  measured at their real width. Other scripts are counted wide, so they get
  cut sooner.
- **Default device names** (`names.default`) must be **one word with no
  spaces**, because players type them in commands.
- **Leave the keys in English.** Only the values are translated.
