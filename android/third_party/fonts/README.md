# Fontes do app

`app/src/main/res/font/` contém instâncias estáticas das fontes variáveis
[Atkinson Hyperlegible Next](https://github.com/googlefonts/atkinson-hyperlegible-next) e
[Atkinson Hyperlegible Mono](https://github.com/googlefonts/atkinson-hyperlegible-next-mono),
baixadas de `google/fonts` (`ofl/atkinsonhyperlegiblenext/` e `ofl/atkinsonhyperlegiblemono/`) em
2026-10-09. Licença: SIL Open Font License 1.1 (arquivos `OFL-*.txt` desta pasta).

Instâncias estáticas em vez da fonte variável: o mesmo arquivo renderiza igual no aparelho e nos
screenshots do Roborazzi (direção visual, `docs/design/direcao-visual.md` §3).

Geradas com fontTools:

```bash
uvx --from fonttools fonttools varLib.instancer --update-name-table \
  -o atkinson_next_regular.ttf "AtkinsonHyperlegibleNext[wght].ttf" wght=400
```

| Arquivo                      | Peso |
| ---------------------------- | ---- |
| `atkinson_next_regular.ttf`  | 400  |
| `atkinson_next_medium.ttf`   | 500  |
| `atkinson_next_semibold.ttf` | 600  |
| `atkinson_next_bold.ttf`     | 700  |
| `atkinson_mono_regular.ttf`  | 400  |
| `atkinson_mono_semibold.ttf` | 600  |
