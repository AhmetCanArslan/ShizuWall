# Tracker Data License

ShizuWall's source code is licensed under **GPLv3** (see [LICENSE.md](LICENSE.md)).
The tracker signature data shipped with the app is **not** code and is covered by
a separate license, documented here.

## Covered files

| File | Origin |
|---|---|
| `app/src/main/assets/trackers.json` | Derived from the Exodus Privacy tracker database |

## License

The Exodus Privacy database is published under the
[Open Database License (ODbL) v1.0](https://opendatacommons.org/licenses/odbl/1-0/).
Rights in the individual contents of the database are licensed under the
[Database Contents License (DbCL) v1.0](https://opendatacommons.org/licenses/dbcl/1-0/).

Both files above are distributed under those same terms.

## Attribution

> Tracker definitions are derived from the [Exodus Privacy](https://exodus-privacy.eu.org/)
> database, licensed under ODbL v1.0.

Exodus Privacy is a French non-profit association. ShizuWall is not affiliated
with, endorsed by, or connected to Exodus Privacy in any way — their data is
simply used under the terms its license grants to everyone.

Consider supporting their work: <https://exodus-privacy.eu.org/en/page/contribute/>

## What was changed

`trackers.json` is a **trimmed** copy of the upstream database, not the raw one.
Long-form descriptions and documentation fields were dropped and the tracker
signature strings were split into a list, reducing 572 KB to 62 KB. Tracker ids,
names, categories and websites are unchanged. The transformation is reproducible
with [`tools/refresh_trackers.py`](tools/refresh_trackers.py).

No definitions are added, removed or edited by hand: the Exodus Privacy database
is the sole source, so what ShizuWall reports is exactly what upstream states.

## No runtime API access

ShizuWall never contacts the Exodus Privacy API. The data ships as an asset and
is refreshed manually before a release. This is deliberate: it keeps the app
offline-only, and it respects the Exodus Privacy
[API documentation](https://github.com/Exodus-Privacy/exodus/blob/v1/doc/api.md),
which asks that end-user products not use their API in production.
