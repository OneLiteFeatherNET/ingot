# Branding

`mark.svg` is the Ingot mark: a cast bar, which is what an ingot is.

It exists because the favicon that used to ship here was Reposilite's `R` logo. The
Apache License 2.0 grants copyright and patent rights, not trademark rights (section 6),
and the [NOTICE](../NOTICE) states that Ingot is not affiliated with or endorsed by the
upstream project. Serving upstream's logo under our product name would contradict both.

> **TODO(onelitefeather):** this is a functional placeholder, not a designed identity.
> Replace it with a real mark and add the wordmark, colour values and a social preview
> image that the docs site can point at.

## Regenerating the raster copies

The SVG is the source. Every PNG in the tree is derived from it:

```bash
rsvg-convert -w 91 -h 91 -f png -o reposilite-backend/src/main/resources/static/favicon.png branding/mark.svg
rsvg-convert -w 91 -h 91 -f png -o reposilite-site/public/images/favicon.png branding/mark.svg
rsvg-convert -w 91 -h 91 -f png -o reposilite-test/workspace/static/favicon.png branding/mark.svg
```

The backend copy is served from an instance's `static/` directory, so an operator can
still drop their own `favicon.png` in the working directory to override it.
