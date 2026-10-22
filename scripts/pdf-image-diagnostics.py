#!/usr/bin/env python3
"""Report embedded image XObjects in a PDF.

Usage: pdf-image-diagnostics.py INPUT.pdf [OUTPUT.md]
"""
import sys, json, re, os
from pypdf import PdfReader

def norm_filter(v):
    if v is None: return None
    if isinstance(v, list): return [str(x) for x in v]
    return str(v)

def norm_cs(v):
    if v is None: return None
    if isinstance(v, list): return [str(x) for x in v]
    return str(v)

def walk_images(resources, prefix="", depth=0, seen=None):
    seen = seen or set()
    out = []
    if resources is None: return out
    xo = resources.get("/XObject")
    if xo is None: return out
    xo = xo.get_object()
    for name, ref in xo.items():
        obj = ref.get_object()
        subtype = str(obj.get("/Subtype"))
        if subtype == "/Form":
            if depth < 8:
                out += walk_images(obj, prefix=f"{prefix}{name}/", depth=depth+1, seen=seen)
        elif subtype == "/Image":
            key = id(obj)
            if key in seen:
                continue
            seen.add(key)
            out.append((f"{prefix}{name}", obj))
    return out

def placements(contents):
    # Return mapping from XObject name to CTM tuple (a,b,c,d,e,f).
    data = contents.get_data() if contents is not None else b""
    current = (1.0,0.0,0.0,1.0,0.0,0.0)
    result = {}
    # sequential scan for cm and Do
    for m in re.finditer(rb'([-+0-9.eE]+\s+[-+0-9.eE]+\s+[-+0-9.eE]+\s+[-+0-9.eE]+\s+[-+0-9.eE]+\s+[-+0-9.eE]+\s+cm)|/(\S+)\s+Do', data):
        if m.group(1):
            nums = re.findall(rb'[-+0-9.eE]+', m.group(1))
            if len(nums) >= 6:
                current = tuple(float(x) for x in nums[:6])
        elif m.group(2):
            name = m.group(2).decode("latin-1")
            result[name] = current
    return result

def dpi_from_ctm(ctm, img_w, img_h):
    a,b,c,d,e,f = ctm
    w_pt = (a*a + c*c) ** 0.5
    h_pt = (b*b + d*d) ** 0.5
    if w_pt <= 0 or h_pt <= 0:
        return None, None
    dpi_x = img_w / (w_pt / 72.0)
    dpi_y = img_h / (h_pt / 72.0)
    return dpi_x, dpi_y

def analyze(path):
    reader = PdfReader(path)
    pages = []
    total_image_bytes = 0
    total_images = 0
    for pi, page in enumerate(reader.pages):
        res = page.get("/Resources")
        res = res.get_object() if res is not None else None
        imgs = walk_images(res)
        place = placements(page.get("/Contents") if "/Contents" in page else None)
        page_images = []
        for name, obj in imgs:
            w = int(obj.get("/Width"))
            h = int(obj.get("/Height"))
            cs = norm_cs(obj.get("/ColorSpace"))
            bpc = obj.get("/BitsPerComponent")
            filt = norm_filter(obj.get("/Filter"))
            if hasattr(obj, "_data") and obj._data is not None:
                length = len(obj._data)
            else:
                try:
                    length = len(obj.get_data())
                except Exception:
                    length = obj.get("/Length")
                    length = int(length) if length is not None else None
            if length: total_image_bytes += length
            total_images += 1
            short_name = name.split("/")[-1]
            ctm = place.get(short_name) or place.get(name)
            if ctm is None:
                # fallback: assume full page
                mb = page.mediabox
                pw = float(mb.width); ph = float(mb.height)
                ctm = (pw,0.0,0.0,ph,0.0,0.0)
            dpi_x, dpi_y = dpi_from_ctm(ctm, w, h)
            page_images.append({
                "name": name,
                "width_px": w,
                "height_px": h,
                "colorspace": cs,
                "bits_per_component": int(bpc) if bpc is not None else None,
                "filter": filt,
                "stream_bytes": length,
                "ctm": [round(x,3) for x in ctm],
                "effective_dpi_x": round(dpi_x,1) if dpi_x else None,
                "effective_dpi_y": round(dpi_y,1) if dpi_y else None,
            })
        pages.append({
            "page": pi+1,
            "mediabox_pt": [float(page.mediabox.left), float(page.mediabox.bottom),
                            float(page.mediabox.right), float(page.mediabox.top)],
            "image_count": len(page_images),
            "images": page_images,
        })
    return {
        "file": os.path.basename(path),
        "path": path,
        "page_count": len(reader.pages),
        "image_xobject_count": total_images,
        "total_image_stream_bytes": total_image_bytes,
        "pages": pages,
    }

def to_markdown(report):
    lines = []
    lines.append(f"# Compression diagnostic: {report['file']}")
    lines.append("")
    lines.append(f"- Pages: {report['page_count']}")
    lines.append(f"- Image XObjects: {report['image_xobject_count']}")
    lines.append(f"- Total image stream bytes: {report['total_image_stream_bytes']}")
    lines.append("")
    for p in report["pages"]:
        lines.append(f"## Page {p['page']}")
        lines.append(f"- MediaBox (pt): {p['mediabox_pt']}")
        lines.append(f"- Images: {p['image_count']}")
        for im in p["images"]:
            lines.append(f"  - {im['name']}: {im['width_px']}x{im['height_px']} px, "
                         f"colorspace={im['colorspace']}, bpc={im['bits_per_component']}, "
                         f"filter={im['filter']}, bytes={im['stream_bytes']}, "
                         f"effDPI={im['effective_dpi_x']}x{im['effective_dpi_y']}")
        lines.append("")
    return "\n".join(lines)

def main():
    if len(sys.argv) < 2:
        print("usage: pdf-image-diagnostics.py INPUT.pdf [OUTPUT.md]", file=sys.stderr)
        sys.exit(2)
    inp = sys.argv[1]
    report = analyze(inp)
    md = to_markdown(report)
    if len(sys.argv) >= 3:
        outp = sys.argv[2]
        os.makedirs(os.path.dirname(outp) or ".", exist_ok=True)
        with open(outp, "w") as f:
            f.write(md)
        # also write json sidecar
        with open(outp.replace(".md",".json"), "w") as f:
            json.dump(report, f, indent=2)
        print(f"wrote {outp}")
    else:
        print(md)

if __name__ == "__main__":
    main()
