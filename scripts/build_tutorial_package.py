#!/usr/bin/env python3
"""
LR-NewazTpoybf Version 1.0 - Cinematic Tutorial Package Generator
Generates the complete tutorial package synchronized with the Android application UI:
- Storyboard.pdf & Storyboard.md
- VoiceOver_EN.docx & VoiceOver_EN.md
- VoiceOver_BN.docx & VoiceOver_BN.md
- Tutorial_Script.pdf & Tutorial_Script.md
- Recording_Checklist.pdf & Recording_Checklist.md
- English.srt
- Bangla.srt
- Thumbnail.png (1920x1080 high-res graphic)
- Veo3_MasterPrompt.txt
- GeminiVideo_MasterPrompt.txt
- Music_Guide.pdf & Music_Guide.md
- Sound_Effects_Guide.pdf & Sound_Effects_Guide.md
- Export_Guide.pdf & Export_Guide.md
"""

import os
import sys
import zipfile
import zlib
import struct
import math

TUTORIAL_DIR = os.path.abspath("tutorial")
PUBLIC_TUTORIAL_DIR = os.path.abspath("public/tutorial")
os.makedirs(TUTORIAL_DIR, exist_ok=True)
os.makedirs(PUBLIC_TUTORIAL_DIR, exist_ok=True)

# ----------------------------------------------------------------------
# Pure Python Multi-Page PDF 1.4 Generator
# ----------------------------------------------------------------------
class SimplePdfWriter:
    def __init__(self, title="LR-Newaz Document"):
        self.title = title
        self.pages = []  # list of list of drawing commands
        self.current_page = []
        self.page_width = 595.28   # A4 width in pt
        self.page_height = 841.89  # A4 height in pt
        self.margin_x = 45.0
        self.margin_y = 50.0
        self.y = self.page_height - self.margin_y

    def start_page(self):
        if self.current_page:
            self.pages.append(self.current_page)
        self.current_page = []
        self.y = self.page_height - self.margin_y

    def check_space(self, needed_pt=20.0):
        if self.y - needed_pt < self.margin_y:
            self.start_page()

    def draw_header_footer(self, page_num, total_pages):
        cmds = []
        # Header line
        cmds.append("q 0.2 0.3 0.5 rg 0.7 w")
        cmds.append(f"{self.margin_x} {self.page_height - 35} m {self.page_width - self.margin_x} {self.page_height - 35} l S")
        # Header text
        cmds.append("BT /F1 8 Tf 0.3 0.3 0.3 rg")
        cmds.append(f"{self.margin_x} {self.page_height - 30} Td (LR-NewazTpoybf v1.0 | Official Cinematic Tutorial Package) Tj ET")
        
        # Footer line
        cmds.append(f"{self.margin_x} 40 m {self.page_width - self.margin_x} 40 l S")
        # Footer text
        cmds.append("BT /F1 8 Tf 0.4 0.4 0.4 rg")
        cmds.append(f"{self.margin_x} 28 Td (Confidential & Proprietary - Bangladesh Land Record Intelligence System) Tj ET")
        cmds.append(f"BT /F1 8 Tf 0.4 0.4 0.4 rg {self.page_width - self.margin_x - 60} 28 Td (Page {page_num} of {total_pages}) Tj ET")
        cmds.append("Q")
        return cmds

    def add_title(self, text, subtitle=None):
        self.check_space(70)
        # Background accent box
        box_top = self.y + 10
        self.current_page.append(f"q 0.08 0.18 0.36 rg {self.margin_x} {box_top - 50} {self.page_width - 2 * self.margin_x} 50 re f Q")
        
        clean_text = self._escape(text)
        self.current_page.append(f"BT /F2 16 Tf 1 1 1 rg {self.margin_x + 14} {box_top - 24} Td ({clean_text}) Tj ET")
        if subtitle:
            clean_sub = self._escape(subtitle)
            self.current_page.append(f"BT /F1 10 Tf 0.85 0.9 0.98 rg {self.margin_x + 14} {box_top - 40} Td ({clean_sub}) Tj ET")
        self.y -= 65

    def add_heading1(self, text):
        self.check_space(35)
        clean_text = self._escape(text)
        # Gold accent bar
        self.current_page.append(f"q 0.85 0.65 0.13 rg {self.margin_x} {self.y - 2} 4 14 re f Q")
        self.current_page.append(f"BT /F2 13 Tf 0.1 0.18 0.3 rg {self.margin_x + 10} {self.y} Td ({clean_text}) Tj ET")
        self.y -= 22

    def add_heading2(self, text):
        self.check_space(26)
        clean_text = self._escape(text)
        self.current_page.append(f"BT /F2 10.5 Tf 0.2 0.3 0.45 rg {self.margin_x + 6} {self.y} Td ({clean_text}) Tj ET")
        self.y -= 16

    def add_paragraph(self, text, indent=0):
        words = text.split()
        lines = []
        cur_line = []
        max_chars = int((self.page_width - 2 * self.margin_x - indent) / 5.2)

        for w in words:
            if len(" ".join(cur_line + [w])) <= max_chars:
                cur_line.append(w)
            else:
                lines.append(" ".join(cur_line))
                cur_line = [w]
        if cur_line:
            lines.append(" ".join(cur_line))

        for line in lines:
            self.check_space(14)
            clean_line = self._escape(line)
            self.current_page.append(f"BT /F1 9 Tf 0.15 0.15 0.15 rg {self.margin_x + indent} {self.y} Td ({clean_line}) Tj ET")
            self.y -= 13
        self.y -= 4

    def add_bullet(self, bold_prefix, text):
        full_text = f"* {bold_prefix} {text}"
        self.add_paragraph(full_text, indent=8)

    def add_table_row(self, col1, col2, col3="", col4="", is_header=False):
        self.check_space(18)
        w = self.page_width - 2 * self.margin_x
        w1 = w * 0.22
        w2 = w * 0.45
        w3 = w * 0.18
        w4 = w * 0.15

        c1 = self._escape(col1[:22])
        c2 = self._escape(col2[:48])
        c3 = self._escape(col3[:18])
        c4 = self._escape(col4[:15])

        if is_header:
            self.current_page.append(f"q 0.15 0.25 0.42 rg {self.margin_x} {self.y - 4} {w} 16 re f Q")
            font = "/F2"
            color = "1 1 1 rg"
        else:
            self.current_page.append(f"q 0.95 0.96 0.98 rg {self.margin_x} {self.y - 4} {w} 15 re f Q")
            self.current_page.append(f"q 0.8 0.82 0.88 rg 0.5 w {self.margin_x} {self.y - 4} m {self.margin_x + w} {self.y - 4} l S Q")
            font = "/F1"
            color = "0.15 0.15 0.15 rg"

        self.current_page.append(f"BT {font} 8.5 Tf {color} {self.margin_x + 4} {self.y} Td ({c1}) Tj ET")
        self.current_page.append(f"BT {font} 8.5 Tf {color} {self.margin_x + w1 + 4} {self.y} Td ({c2}) Tj ET")
        if col3:
            self.current_page.append(f"BT {font} 8.5 Tf {color} {self.margin_x + w1 + w2 + 4} {self.y} Td ({c3}) Tj ET")
        if col4:
            self.current_page.append(f"BT {font} 8.5 Tf {color} {self.margin_x + w1 + w2 + w3 + 4} {self.y} Td ({c4}) Tj ET")
        self.y -= 17

    def _escape(self, s):
        sanitized = []
        for ch in s:
            val = ord(ch)
            if val < 32 or val > 126:
                if ch in ['\n', '\r']:
                    sanitized.append(' ')
                elif val == 8220 or val == 8221:
                    sanitized.append('"')
                elif val == 8216 or val == 8217:
                    sanitized.append("'")
                elif val == 8211 or val == 8212:
                    sanitized.append("-")
                elif val == 8226:
                    sanitized.append("*")
                else:
                    pass
            elif ch in ('\\', '(', ')'):
                sanitized.append('\\' + ch)
            else:
                sanitized.append(ch)
        return "".join(sanitized)

    def write_to_file(self, filepath):
        if self.current_page:
            self.pages.append(self.current_page)
        total_pages = max(1, len(self.pages))

        objects = []
        n_pages = len(self.pages)
        font_f1_id = 3 + 2 * n_pages
        font_f2_id = font_f1_id + 1

        objects.append("1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj")
        kids_refs = " ".join([f"{3 + i} 0 R" for i in range(n_pages)])
        objects.append(f"2 0 obj\n<< /Type /Pages /Kids [{kids_refs}] /Count {n_pages} >>\nendobj")

        for i in range(n_pages):
            page_id = 3 + i
            content_id = 3 + n_pages + i
            objects.append(
                f"{page_id} 0 obj\n"
                f"<< /Type /Page /Parent 2 0 R\n"
                f"   /MediaBox [0 0 {self.page_width:.2f} {self.page_height:.2f}]\n"
                f"   /Contents {content_id} 0 R\n"
                f"   /Resources << /Font << /F1 {font_f1_id} 0 R /F2 {font_f2_id} 0 R >> >>\n"
                f">>\nendobj"
            )

        for i, page_cmds in enumerate(self.pages):
            content_id = 3 + n_pages + i
            all_cmds = self.draw_header_footer(i + 1, total_pages) + page_cmds
            stream_body = "\n".join(all_cmds).encode('latin-1', errors='replace')
            objects.append(
                f"{content_id} 0 obj\n"
                f"<< /Length {len(stream_body)} >>\n"
                f"stream\n" + stream_body.decode('latin-1') + "\nendstream\nendobj"
            )

        objects.append(f"{font_f1_id} 0 obj\n<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>\nendobj")
        objects.append(f"{font_f2_id} 0 obj\n<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica-Bold >>\nendobj")

        pdf_bytes = bytearray(b"%PDF-1.4\n%\xe2\xe3\xcf\xd3\n")
        offsets = [0]
        for obj in objects:
            offsets.append(len(pdf_bytes))
            pdf_bytes.extend(obj.encode('latin-1') + b"\n")

        startxref = len(pdf_bytes)
        total_objs = len(objects) + 1
        xref = [f"xref\n0 {total_objs}\n0000000000 65535 f \n"]
        for off in offsets[1:]:
            xref.append(f"{off:010d} 00000 n \n")
        pdf_bytes.extend("".join(xref).encode('latin-1'))
        trailer = (
            f"trailer\n"
            f"<< /Size {total_objs}\n"
            f"   /Root 1 0 R\n"
            f">>\n"
            f"startxref\n"
            f"{startxref}\n"
            f"%%EOF\n"
        )
        pdf_bytes.extend(trailer.encode('latin-1'))

        with open(filepath, 'wb') as f:
            f.write(pdf_bytes)
        print(f"Generated PDF: {filepath} ({len(pdf_bytes):,} bytes, {n_pages} pages)")


# ----------------------------------------------------------------------
# Pure Python DOCX Generator with Full UTF-8 (Bengali & English) Support
# ----------------------------------------------------------------------
def generate_docx(filepath, title, sections):
    def xml_esc(s):
        return (str(s).replace("&", "&amp;")
                      .replace("<", "&lt;")
                      .replace(">", "&gt;")
                      .replace('"', "&quot;")
                      .replace("'", "&apos;"))

    content_types = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
    <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
    <Default Extension="xml" ContentType="application/xml"/>
    <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
    <Override PartName="/word/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml"/>
</Types>"""

    rels = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
    <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
</Relationships>"""

    doc_rels = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
    <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
</Relationships>"""

    styles_xml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:styles xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
    <w:docDefaults>
        <w:rPrDefault>
            <w:rPr>
                <w:rFonts w:ascii="Calibri" w:hAnsi="Calibri" w:cs="Kalpurush"/>
                <w:sz w:val="22"/>
            </w:rPr>
        </w:rPrDefault>
    </w:docDefaults>
</w:styles>"""

    body_xml_parts = []
    body_xml_parts.append(
        f'<w:p><w:pPr><w:jc w:val="center"/><w:spacing w:after="240"/></w:pPr>'
        f'<w:r><w:rPr><w:b/><w:color w:val="0F2042"/><w:sz w:val="36"/></w:rPr>'
        f'<w:t>{xml_esc(title)}</w:t></w:r></w:p>'
    )
    body_xml_parts.append(
        f'<w:p><w:pPr><w:jc w:val="center"/><w:spacing w:after="400"/></w:pPr>'
        f'<w:r><w:rPr><w:i/><w:color w:val="555555"/><w:sz w:val="22"/></w:rPr>'
        f'<w:t>LR-NewazTpoybf Version 1.0 Production User Guide Tutorial Voiceover</w:t></w:r></w:p>'
    )

    for heading, paras in sections:
        body_xml_parts.append(
            f'<w:p><w:pPr><w:spacing w:before="280" w:after="120"/></w:pPr>'
            f'<w:r><w:rPr><w:b/><w:color w:val="1E40AF"/><w:sz w:val="28"/></w:rPr>'
            f'<w:t>{xml_esc(heading)}</w:t></w:r></w:p>'
        )
        for p in paras:
            if isinstance(p, tuple):
                label, text = p
                body_xml_parts.append(
                    f'<w:p><w:pPr><w:spacing w:after="100"/></w:pPr>'
                    f'<w:r><w:rPr><w:b/><w:color w:val="D97706"/><w:sz w:val="22"/></w:rPr>'
                    f'<w:t>{xml_esc(label)}: </w:t></w:r>'
                    f'<w:r><w:rPr><w:sz w:val="22"/></w:rPr>'
                    f'<w:t xml:space="preserve">{xml_esc(text)}</w:t></w:r></w:p>'
                )
            else:
                body_xml_parts.append(
                    f'<w:p><w:pPr><w:spacing w:after="100"/></w:pPr>'
                    f'<w:r><w:rPr><w:sz w:val="22"/></w:rPr>'
                    f'<w:t>{xml_esc(p)}</w:t></w:r></w:p>'
                )

    document_xml = f"""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
    <w:body>
        {"".join(body_xml_parts)}
    </w:body>
</w:document>"""

    with zipfile.ZipFile(filepath, 'w', zipfile.ZIP_DEFLATED) as zf:
        zf.writestr('[Content_Types].xml', content_types.encode('utf-8'))
        zf.writestr('_rels/.rels', rels.encode('utf-8'))
        zf.writestr('word/_rels/document.xml.rels', doc_rels.encode('utf-8'))
        zf.writestr('word/styles.xml', styles_xml.encode('utf-8'))
        zf.writestr('word/document.xml', document_xml.encode('utf-8'))

    print(f"Generated DOCX: {filepath} ({os.path.getsize(filepath):,} bytes)")


# ----------------------------------------------------------------------
# Pure Python 1920x1080 Thumbnail PNG Generator
# ----------------------------------------------------------------------
def generate_thumbnail_png(filepath):
    width = 1920
    height = 1080

    raw_scanlines = bytearray(height * (1 + width * 4))

    bg_top_r, bg_top_g, bg_top_b = 7, 14, 30
    bg_bot_r, bg_bot_g, bg_bot_b = 20, 33, 61

    phone_x = 1200
    phone_y = 120
    phone_w = 580
    phone_h = 840
    screen_x = phone_x + 20
    screen_y = phone_y + 40
    screen_w = phone_w - 40
    screen_h = phone_h - 80

    offset = 0
    for y in range(height):
        raw_scanlines[offset] = 0
        offset += 1
        
        gy = y / height
        base_r = int(bg_top_r + (bg_bot_r - bg_top_r) * gy)
        base_g = int(bg_top_g + (bg_bot_g - bg_top_g) * gy)
        base_b = int(bg_top_b + (bg_bot_b - bg_top_b) * gy)

        for x in range(width):
            r, g, b = base_r, base_g, base_b

            if (x % 60 == 0 or y % 60 == 0) and (x < 1150 or y > 980 or y < 100):
                r = min(255, r + 15)
                g = min(255, g + 28)
                b = min(255, b + 48)

            beam_dist = abs((x * 0.7 + y * 0.7) - 800)
            if beam_dist < 220:
                glow = int((1.0 - beam_dist / 220) * 35)
                r = min(255, r + int(glow * 0.6))
                g = min(255, g + int(glow * 0.9))
                b = min(255, b + glow)

            if 160 <= y <= 205 and 120 <= x <= 750:
                r, g, b = 20, 45, 90
                if y == 160 or y == 205 or x == 120 or x == 750:
                    r, g, b = 230, 180, 40

            if 230 <= y <= 380 and 120 <= x <= 1080:
                r = int(r * 0.4 + 25)
                g = int(g * 0.4 + 40)
                b = int(b * 0.4 + 75)
                if y in (230, 380) or x in (120, 1080):
                    r, g, b = 245, 190, 35

            if 410 <= y <= 540 and 120 <= x <= 1080:
                r, g, b = 235, 160, 20
                if (x + y) % 30 < 2:
                    r, g, b = 255, 190, 50
                if y in (410, 540) or x in (120, 1080):
                    r, g, b = 255, 255, 255

            if 570 <= y <= 650 and 120 <= x <= 1080:
                r, g, b = 15, 75, 55
                if y in (570, 650) or x in (120, 1080):
                    r, g, b = 46, 204, 113

            if 690 <= y <= 760 and 120 <= x <= 400:
                r, g, b = 30, 40, 65
                if y in (690, 760) or x in (120, 400): r, g, b = 100, 180, 255
            if 690 <= y <= 760 and 420 <= x <= 740:
                r, g, b = 30, 40, 65
                if y in (690, 760) or x in (420, 740): r, g, b = 46, 204, 113
            if 690 <= y <= 760 and 760 <= x <= 1080:
                r, g, b = 30, 40, 65
                if y in (690, 760) or x in (760, 1080): r, g, b = 255, 170, 0

            if 810 <= y <= 880 and 120 <= x <= 1080:
                r, g, b = 12, 22, 45
                if y in (810, 880) or x in (120, 1080): r, g, b = 200, 160, 40

            if phone_x <= x <= phone_x + phone_w and phone_y <= y <= phone_y + phone_h:
                r, g, b = 25, 28, 35
                if (x == phone_x or x == phone_x + phone_w or y == phone_y or y == phone_y + phone_h):
                    r, g, b = 180, 190, 205
                elif (x in (phone_x+1, phone_x + phone_w - 1) or y in (phone_y+1, phone_y + phone_h - 1)):
                    r, g, b = 80, 90, 105

                if screen_x <= x <= screen_x + screen_w and screen_y <= y <= screen_y + screen_h:
                    r, g, b = 242, 235, 218

                    px = x - screen_x
                    py = y - screen_y

                    if py < 65:
                        r, g, b = 15, 30, 65
                    elif py > screen_h - 60:
                        r, g, b = 20, 35, 75
                    else:
                        is_grid = (px % 75 == 0 or py % 75 == 0)
                        is_diag = ((px * 2 + py) % 110 == 0) or ((px - py * 1.5) % 130 == 0)
                        if is_grid or is_diag:
                            r, g, b = 110, 95, 80

                        in_poly = (210 <= px <= 380 and 220 <= py <= 430) and not (px > 330 and py > 370)
                        if in_poly:
                            r, g, b = 40, 185, 105
                            if px in (210, 211, 379, 380) or py in (220, 221, 429, 430):
                                r, g, b = 255, 215, 0

                        in_poly2 = (80 <= px <= 200 and 220 <= py <= 380)
                        if in_poly2 and (px in (80, 81, 199, 200) or py in (220, 221, 379, 380)):
                            r, g, b = 230, 110, 70

                        if (px - 295)**2 + (py - 310)**2 <= 400:
                            r, g, b = 255, 215, 0

            if x < 8 or x >= width - 8 or y < 8 or y >= height - 8:
                r, g, b = 220, 175, 45

            raw_scanlines[offset]     = r
            raw_scanlines[offset + 1] = g
            raw_scanlines[offset + 2] = b
            raw_scanlines[offset + 3] = 255
            offset += 4

    compressed_idat = zlib.compress(raw_scanlines, level=6)

    def png_chunk(chunk_type, data):
        length = struct.pack(">I", len(data))
        crc = struct.pack(">I", zlib.crc32(chunk_type + data) & 0xffffffff)
        return length + chunk_type + data + crc

    png_bytes = bytearray(b"\x89PNG\r\n\x1a\n")
    ihdr_data = struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0)
    png_bytes.extend(png_chunk(b"IHDR", ihdr_data))
    png_bytes.extend(png_chunk(b"IDAT", compressed_idat))
    png_bytes.extend(png_chunk(b"IEND", b""))

    with open(filepath, 'wb') as f:
        f.write(png_bytes)
    print(f"Generated Thumbnail PNG: {filepath} ({len(png_bytes):,} bytes, 1920x1080)")


# ----------------------------------------------------------------------
# Build Deliverable Contents
# ----------------------------------------------------------------------
SCENES = [
    {
        "num": 1,
        "title": "Cinematic Introduction & Brand Identity",
        "time": "00:00 - 00:35 (35s)",
        "screen_action": "High-contrast dynamic launch animation of LR-NewazTpoybf Version 1.0. The screen zooms in smoothly onto the Bangladesh Land Record Extractor emblem, transitioning into the application's clean Material 3 Parchment & Sapphire theme.",
        "camera": "Smooth slow zoom-in from 100% to 115%, soft aperture depth-of-field, subtle floating light particles on dark sapphire background.",
        "transition": "Smooth cinematic cross-dissolve with subtle motion blur into the device frame.",
        "text_overlay": "LR-NewazTpoybf v1.0 | Bangladesh Land Record Intelligence Platform",
        "narration_en": "Welcome to LR-Newaz Version 1.0, the definitive forensic intelligence platform designed specifically for Bangladesh land records. From historical handwritten Khatians to complex Cadastral survey maps, this complete user tutorial will guide you step-by-step through every feature.",
        "narration_bn": "এলআর-নেওয়াজ ভার্সন ১.০-এ আপনাকে স্বাগতম। এটি বাংলাদেশ ভূমি রেকর্ড বিশ্লেষণ ও সুরক্ষার জন্য একটি আধুনিক ফরেনসিক প্ল্যাটফর্ম। ঐতিহাসিক হস্তলিখিত খতিয়ান থেকে শুরু করে জটিল মৌজা নকশা ও ওয়ারিশনামা মেলানো—এই সম্পূর্ণ টিউটোরিয়ালটিতে অ্যাপটির প্রতিটি ফিচার সহজে দেখানো হয়েছে।",
        "ui_elements": "Splash Logo, App Title, Offline Verification Badge, Build Version 1.0.0."
    },
    {
        "num": 2,
        "title": "Standalone APK Installation & Zero-Permission Privacy",
        "time": "00:35 - 01:15 (40s)",
        "screen_action": "User downloads app-debug.apk from the portal, opens Android Package Installer, confirms installation with zero unnecessary permissions, and launches the app directly into offline mode.",
        "camera": "Top-down 45-degree angle macro zoom focusing on the Android Package Installer modal, panning down to the 'Install' and 'Open' buttons.",
        "transition": "Clean lateral slide transition right-to-left.",
        "text_overlay": "Step 1: Install Standalone APK (app-debug.apk) | 100% Zero-Permission Privacy",
        "narration_en": "Getting started is completely seamless. Download the standalone APK file. LR-Newaz requires zero dangerous media permissions, strictly utilizing Android's scoped Storage Access Framework to keep all your family deed records 100% private on your own device.",
        "narration_bn": "শুরু করা অত্যন্ত সহজ। সরাসরি স্ট্যান্ডঅ্যালোন এপিকে ফাইলটি ইনস্টল করুন। এই অ্যাপে কোনো বিপজ্জনক মিডিয়া পারমিশনের প্রয়োজন নেই। এটি অ্যান্ড্রয়েডের নিরাপদ 'স্টোরেজ অ্যাক্সেস ফ্রেমওয়ার্ক' ব্যবহার করে আপনার প্রতিটি দলিলের গোপনীয়তা শতভাগ নিশ্চিত করে।",
        "ui_elements": "Package Installer, App Icon, 'Install' Button, Android Home Screen Launcher Icon."
    },
    {
        "num": 3,
        "title": "Language Selection & Tesseract Offline OCR Engine Setup",
        "time": "01:15 - 02:00 (45s)",
        "screen_action": "User opens the top AppBar menu, taps the MenuBook icon (testTag: 'tesseract_setup_button'), navigates to TesseractSetupScreen, verifies Bengali (ben.traineddata) and English (eng.traineddata) language packs, and checks the green verified engine status indicator.",
        "camera": "Close-up macro pan from top right AppBar icon down to the language pack verification cards with soft glow effects on the status badge.",
        "transition": "Subtle zoom-out transition returning to the main dashboard.",
        "text_overlay": "Step 2: Language & OCR Pack Setup | Tesseract 5.x Offline Traineddata",
        "narration_en": "Upon first launch, tap the OCR book icon in the top bar to inspect your offline language packs. The pre-bundled Bengali and English trained data files guarantee lightning-fast character recognition without needing an active internet connection.",
        "narration_bn": "প্রথমবার চালু করার পর উপরের মেনু বুক আইকনে ট্যাপ করে অফলাইন ওসিআর ভাষা সেটআপ দেখুন। বাংলা এবং ইংরেজি মডেলগুলো আগেই প্রস্তুত থাকে, যাতে ইন্টারনেট ছাড়াই আপনার ডিভাইসে তাৎক্ষণিক অক্ষর শনাক্তকরণ সম্ভব হয়।",
        "ui_elements": "TopAppBar 'tesseract_setup_button', TesseractSetupScreen, Bengali Pack Status, Engine Initialized Card."
    },
    {
        "num": 4,
        "title": "Home Dashboard, Forensic Metrics & Legal Disclaimer",
        "time": "02:00 - 02:40 (40s)",
        "screen_action": "Overview of HomeScreen. Demonstration of the mandatory Legal Disclaimer Banner, real-time forensic metric counters (Total Documents, Verified, Zero-Byte Errors), and the Quick CSV External Reporting bar.",
        "camera": "Gentle downward vertical pan across the Legal Disclaimer, metric counter cards, and search bar.",
        "transition": "Split-second cross-fade with amber highlight pulse around the disclaimer banner.",
        "text_overlay": "Step 3: Home Dashboard Overview | Real-Time Audit Metrics",
        "narration_en": "The Home Dashboard provides an instant snapshot of your archive. Notice the legal disclaimer highlighting automated forensic assistance, alongside real-time metric cards showing total documents, verified status, and zero-byte error detection.",
        "narration_bn": "হোম ড্যাশবোর্ড আপনার দলিলের সার্বিক অবস্থা একনজরে তুলে ধরে। এখানে আইনি সচেতনতামূলক ব্যানার, মোট নথি সংখ্যা, যাচাইকৃত রেকর্ডের অনুপাত এবং ত্রুটি শনাক্তকারী কার্ড সরাসরি প্রদর্শিত হয়।",
        "ui_elements": "LegalDisclaimerBanner, MetricCard ('মোট নথি', 'যাচাইকৃত', '০-বাইট ত্রুটি'), 'btn_export_csv_quick'."
    },
    {
        "num": 5,
        "title": "Bulk Document Ingestion & Storage Access Framework (SAF)",
        "time": "02:40 - 03:30 (50s)",
        "screen_action": "User taps the Extended FAB 'ইনপুট ফোল্ডার (SAF)' (testTag: 'fab_saf_folder') or the Bulk Upload icon (testTag: 'btn_bulk_upload_nav'). User selects a batch folder containing PDF deeds and JPEG scans. The upload queue displays live progress, integrity hashing, and zero-byte safety validation.",
        "camera": "Dynamic push-in on the Floating Action Button, transitioning to the SAF system tree, then panning down the active ingestion queue.",
        "transition": "Card expansion zoom effect from button to full screen.",
        "text_overlay": "Step 4: Bulk Ingestion via SAF | Scoped Storage & Zero-Byte Safety",
        "narration_en": "To import records, tap the 'Input Folder' floating button. Select an entire folder of scans or PDF deeds. The bulk upload engine validates each file, checks cryptographic SHA-256 hashes, and filters out corrupted or zero-byte files automatically.",
        "narration_bn": "নথি যুক্ত করতে 'ইনপুট ফোল্ডার' বাটনে ট্যাপ করুন। স্ক্যানকৃত ছবি বা পিডিএফ ফোল্ডার নির্বাচন করলে বাল্ক আপলোড ইঞ্জিন প্রতিটি ফাইলের ডিজিটাল হ্যাশ যাচাই করে এবং ত্রুটিপূর্ণ ফাইল বাদ দিয়ে নিরাপদ আপলোড সম্পন্ন করে।",
        "ui_elements": "FAB 'fab_saf_folder', BulkUploadScreen, ProgressBar, 'btn_start_batch_processing'."
    },
    {
        "num": 6,
        "title": "High-Contrast Document OCR & Adaptive Binarization",
        "time": "03:30 - 04:20 (50s)",
        "screen_action": "Demonstration of OCR processing. The app applies automated deskewing, Otsu adaptive binarization to faded ink, and extracts tabular Khatian structures including Mouza, JL Number, CS/SA/RS/BRS classification, and owner shares with confidence scores.",
        "camera": "Cinematic vertical split-screen comparison: Left side showing the faded yellowed historical deed scan, Right side showing the crisp binarized text with green confidence bounding boxes.",
        "transition": "Vertical scanning laser bar moving from left to right revealing the recognized digital text.",
        "text_overlay": "Step 5: Tabular OCR Extraction | Deskewing, Otsu Binarization & Confidence Scoring",
        "narration_en": "Watch the AI engine handle aged, faded parchment. Through adaptive binarization and morphological filtering, the system extracts tabular Khatian fields, owner names, and share ratios, color-coding high confidence in green and uncertain fields in amber.",
        "narration_bn": "পুরোনো ও বিবর্ণ দলিলের ক্ষেত্রে দেখুন কীভাবে অ্যাপটি অ্যাডাপ্টিভ বাইনারাইজেশন ব্যবহার করে লেখা পরিষ্কার করে। খতিয়ান নম্বর, জেএল নম্বর, মালিকের নাম এবং হিস্যা নিখুঁতভাবে উদ্ধার করে কনফিডেন্স স্কোরসহ উপস্থাপন করে।",
        "ui_elements": "DocumentDetailScreen, FieldConfidenceChip (VERIFIED / PROBABLE), Extracted Table Grid."
    },
    {
        "num": 7,
        "title": "Module 04: Bangla Handwriting Recognition (HCR) & Marginalia Review",
        "time": "04:20 - 05:15 (55s)",
        "screen_action": "User taps the HCR banner on Home (testTag: 'btn_open_hcr') or navigates to HcrCorrectionScreen. The UI presents cursive Bengali scribbles, official revenue stamps, and marginal endorsements. User reviews confidence chips, edits uncertain tokens, and tags marginal notes.",
        "camera": "Tight macro zoom on cursive Bengali handwriting sample, highlighting token chips with animated tooltip popups showing confidence percentages.",
        "transition": "Horizontal slide from handwriting crop to text editing field.",
        "text_overlay": "Step 6: Bangla Handwriting Recognition (HCR) | Marginalia & Revenue Stamp Recovery",
        "narration_en": "Module 04 is our breakthrough Bangla Handwriting Recognition engine. It deciphers cursive scribbles, revenue stamps, and marginal endorsements. You can inspect individual token confidence scores, make real-time corrections, and insert forensic provenance tags.",
        "narration_bn": "মডিউল ০৪-এ রয়েছে বাংলা হস্তলিপি শনাক্তকরণ প্রযুক্তি। এটি দলিলের মার্জিনের হস্তলিখিত নোট এবং পুরোনো সিলমোহর পাঠোদ্ধার করে। প্রতিটি শব্দের কনফিডেন্স চিপ দেখে ব্যবহারকারী তাৎক্ষণিক সংশোধন ও ফরেনসিক ট্যাগ যোগ করতে পারেন।",
        "ui_elements": "HcrCorrectionScreen, 'card_hcr_banner', TokenConfidenceChip, MarginaliaCard, 'btn_save_hcr_corrections'."
    },
    {
        "num": 8,
        "title": "Module 05: Cadastral Survey Map Intelligence & Polygon Detection",
        "time": "05:15 - 06:10 (55s)",
        "screen_action": "Demonstration of Cadastral Map GIS detection. The engine analyzes CS, SA, RS, and BS survey sheets, detecting plot polygons, Bengali digit plot numbers, road corridors, water bodies, and the official north arrow with sub-pixel contour approximation.",
        "camera": "Smooth 2D pan across a high-resolution Cadastral survey map sheet, zooming into Dag plot boundaries as glowing vector outlines snap into place.",
        "transition": "Holographic grid overlay fade-in turning raster map into interactive vector polygons.",
        "text_overlay": "Step 7: Cadastral Map Intelligence | Vector Polygon Extraction & Bengali Digit Detection",
        "narration_en": "In Module 05, the Cadastral Survey Map Intelligence engine processes complex survey sheets. It automatically vectors plot contours, recognizes Bengali Dag numbers, identifies water bodies and roads, and aligns the true north orientation.",
        "narration_bn": "মডিউল ০৫-এ মৌজা নকশা বা সিএস, এসএ ও আরএস ম্যাপ প্রসেসিং দেখানো হয়েছে। এটি নকশার প্লটগুলোকে ভেক্টর পলিগনে রূপান্তর করে, বাংলা দাগ নম্বর চিহ্নিত করে এবং রাস্তা ও জলাশয় নির্ভুলভাবে পৃথক করে।",
        "ui_elements": "CadastralMapView, DagPolygonHighlight, RoadNetworkOverlay, NorthArrowIndicator."
    },
    {
        "num": 9,
        "title": "Module 06: Warishnama Inheritance Matching & Genealogical Links",
        "time": "06:10 - 07:15 (65s)",
        "screen_action": "User taps 'ওয়ারিশনামা ও মৌজা নকশা মেলানো' (testTag: 'btn_open_warish_matcher') to enter WarishMatcherScreen. User selects a Warish certificate, cross-references genealogical heirs against CS/SA/RS ancestor titles, reviews legal share calculations (আনা-গণ্ডা-কড়া-ক্রান্তি), and inspects forensic audit tiers.",
        "camera": "Medium pan following the genealogical tree hierarchy descending from deceased landholder to lawful heirs with connecting gold links.",
        "transition": "Accordion fold animation expanding heir shares and evidence tiers.",
        "text_overlay": "Step 8: Warishnama Inheritance Matching | Muslim & Hindu Succession & Forensic Evidence Tiers",
        "narration_en": "Module 06 delivers automated Warishnama inheritance matching. It calculates precise shares under statutory inheritance rules, cross-matches genealogical heirs against historical Khatians, and classifies links into forensic tiers from Verified to Record Gap.",
        "narration_bn": "মডিউল ০৬-এ ওয়ারিশনামা ম্যাচিং ব্যবস্থা কার্যকর। এটি ওয়ারিশদের উত্তরাধিকার আইন অনুযায়ী আনা-গণ্ডা-কড়া-ক্রান্তিতে হিস্যা হিসাব করে এবং পূর্বপুরুষের সিএস বা এসএ রেকর্ডের সাথে মিলিয়ে ফরেনসিক প্রমাণপত্র প্রস্তুত করে।",
        "ui_elements": "WarishMatcherScreen, 'btn_open_warish_matcher', HeirShareCard, EvidenceTierBadge (VERIFIED / CORROBORATED / GAP)."
    },
    {
        "num": 10,
        "title": "Cadastral Dag Highlighting & Interactive Parcel Inspection",
        "time": "07:15 - 08:05 (50s)",
        "screen_action": "Within WarishMatcherScreen and AtlasTimelineScreen, user taps on an heir's entitlement. Instantly, corresponding Dag plots (e.g. Dag 402, 405) illuminate in bright emerald green on the Cadastral map canvas, while adjacent plots pulse in amber.",
        "camera": "Dynamic 3D tilt and rapid smooth zoom focusing on Dag Plot #402 as the emerald highlight pulses with a golden perimeter halo.",
        "transition": "Circular iris zoom expanding outward from the centroid of Dag 402.",
        "text_overlay": "Step 9: Highlight Matching Lands | Emerald Green Highlighting of Validated Dag Plots",
        "narration_en": "Notice how the system immediately bridges records and geography. Tapping an heir's entitlement instantly highlights their validated land plots on the Cadastral map in vivid emerald green, allowing surveyors and landowners to inspect exact physical boundaries.",
        "narration_bn": "লক্ষ্য করুন কীভাবে তথ্যের সাথে নকশার মেলবন্ধন ঘটে। ওয়ারিশের হিস্যায় ট্যাপ করলেই সংশ্লিষ্ট দাগটি (যেমন দাগ ৪০২) মৌজা নকশায় জ্বলজ্বলে পান্না সবুজ রঙে হাইলাইট হয়, যা জমি শনাক্তকরণ অত্যন্ত সহজ করে দেয়।",
        "ui_elements": "InteractiveMapCanvas, HighlightedDagPlot ('দাগ ৪০২'), LandParcelPopup, AreaSummaryBadge."
    },
    {
        "num": 11,
        "title": "Module 13 & 14: Historical Property Atlas & Forensic Validation Engine",
        "time": "08:05 - 09:05 (60s)",
        "screen_action": "User opens Historical Atlas from Home (testTag: 'card_atlas_banner'). Demonstrates the 4-era timeline (CS 1910 -> SA 1956 -> RS 1972 -> BS 1998), visualizing how Dag 402 was partitioned into sub-plots 402/1 and 402/2. Shows discrepancy alerts for area mismatch and honorific name variations.",
        "camera": "Horizontal tracking camera sliding along the timeline chronometer, followed by split-screen tree branching visualization.",
        "transition": "Time-lapse slider transition moving from year 1910 through 1998.",
        "text_overlay": "Step 10: Historical Property Atlas | CS -> SA -> RS -> BS Chain & Dag Split Detection",
        "narration_en": "Modules 13 and 14 introduce the Historical Property Atlas and Forensic Validation Engine. Trace your property through every major survey across a century: CS, SA, RS, and BS. The engine automatically detects Dag splits, area discrepancies, and historical name variants.",
        "narration_bn": "মডিউল ১৩ ও ১৪-এ রয়েছে ঐতিহাসিক প্রপার্টি অ্যাটলাস। গত এক শতাব্দীর সিএস, এসএ, আরএস ও বিএস রেকর্ডের ধারাবাহিকতা ট্র্যাক করুন। দাগের বিভাজন, জমির পরিমাণের অমিল এবং নামের বানান বৈচিত্র্য স্বয়ংক্রিয়ভাবে ধরা পড়ে।",
        "ui_elements": "AtlasTimelineScreen, 'card_atlas_banner', TimelineNode (CS/SA/RS/BS), DagSplitFlowDiagram, DiscrepancyAlertCard."
    },
    {
        "num": 12,
        "title": "High-Speed Offline Search & Multi-Criteria Filtering",
        "time": "09:05 - 09:50 (45s)",
        "screen_action": "User returns to HomeScreen, types 'মৌজা রূপগঞ্জ' or 'দাগ ৪০২' into the search field (testTag: 'search_input'). Instant full-text search results filter the document list within milliseconds using local SQLite FTS.",
        "camera": "Close-up on typing in the search bar, with instant reactive filtering of the list below, accompanied by soft amber highlight pulses on matched keywords.",
        "transition": "Snappy dissolve into filtered search results.",
        "text_overlay": "Step 11: Instant Offline Search | SQLite Full-Text Search (FTS) by Khatian, Dag & Owner",
        "narration_en": "Finding any deed or plot in your archive is instantaneous. Use the search bar to query by Khatian number, Dag plot, Mouza, or owner name in English or Bengali. SQLite Full-Text Search delivers results locally in under twenty milliseconds.",
        "narration_bn": "আপনার সংগ্রহে থাকা যেকোনো দলিল খুঁজে নেওয়া চোখের পলকের ব্যাপার। সার্চ বারে খতিয়ান, দাগ, মৌজা কিংবা মালিকের নাম বাংলায় বা ইংরেজিতে লিখুন। অফলাইন সার্চ মাত্র কয়েক মিলি-সেকেন্ডে কাঙ্ক্ষিত নথি হাজির করবে।",
        "ui_elements": "OutlinedTextField 'search_input', CapturedRecordCard, HighlightedSearchTerms."
    },
    {
        "num": 13,
        "title": "Multi-Format Forensic Report Export (PDF, Excel, GeoJSON, ZIP)",
        "time": "09:50 - 10:45 (55s)",
        "screen_action": "User taps 'রিপোর্ট সংরক্ষণ (SAF)' or opens DocumentDetailScreen export menu. User selects PDF Court Dossier, Excel XLSX Workbook, Cadastral GeoJSON, or All-in-One Forensic ZIP Archive. Android SAF dialog saves the files to device storage with zero permissions.",
        "camera": "Hero showcase view of the generated multi-page bilingual PDF dossier, zooming into the forensic stamp, followed by Excel and GeoJSON asset cards.",
        "transition": "Smooth 3D page flip effect showcasing the exported PDF document.",
        "text_overlay": "Step 12: Multi-Format Forensic Export | Court Dossier PDF, Excel, GeoJSON & ZIP",
        "narration_en": "Exporting court-ready reports is effortless. Choose from professional bilingual PDF Dossiers with cryptographic stamps, comprehensive Excel workbooks, GIS GeoJSON layers, or packaged forensic ZIP archives for legal and surveying submissions.",
        "narration_bn": "আদালত বা সরকারি অফিসে জমা দেওয়ার জন্য রিপোর্ট এক্সপোর্ট করা অত্যন্ত সহজ। প্রাতিষ্ঠানিক সিলযুক্ত দ্বিভাষিক পিডিএফ ডজিয়ার, এক্সেল ওয়ার্কবুক, জিআইএস জিওজেসন কিংবা জিপ ফাইল আকারে এক ক্লিকেই সংরক্ষণ করুন।",
        "ui_elements": "ReportExportDialog, 'export_csv_button', FormatRadioGroup (PDF / EXCEL / GEOJSON / ZIP), SuccessSnackBar."
    },
    {
        "num": 14,
        "title": "Offline Security, Summary & Next Steps",
        "time": "10:45 - 11:30 (45s)",
        "screen_action": "Final review of system architecture: 100% offline capability, local Room database, zero cloud leaks, and verified build stability. Shows the official GitHub repository, documentation links, and conclusion.",
        "camera": "Smooth pull-back from mobile device to high-tech desk environment, fading up the LR-NewazTpoybf Version 1.0 golden badge and documentation links.",
        "transition": "Slow cinematic fade to black with final branding logo.",
        "text_overlay": "LR-NewazTpoybf Version 1.0 | Offline • Private • Forensic • Complete",
        "narration_en": "You are now fully equipped to utilize LR-Newaz Version 1.0. Completely offline, forensically verifiable, and built to safeguard Bangladesh land heritage. Download the release, explore the documentation, and master your land records with total confidence.",
        "narration_bn": "এখন আপনি এলআর-নেওয়াজ ভার্সন ১.০ ব্যবহারে সম্পূর্ণ প্রস্তুত। শতভাগ অফলাইন, ফরেনসিক প্রমাণের দিক থেকে নিখুঁত এবং বাংলাদেশের ঐতিহ্যবাহী ভূমি সুরক্ষায় নিবেদিত। আজই ডাউনলোড করুন এবং আত্মবিশ্বাসের সাথে ব্যবহার শুরু করুন। ধন্যবাদ।",
        "ui_elements": "FinalSummaryCard, DocumentationLinks, Version1Badge, ExitCredits."
    }
]

# ----------------------------------------------------------------------
# 1. Storyboard Documentation Generator (PDF & MD)
# ----------------------------------------------------------------------
def build_storyboard():
    md_lines = [
        "# LR-NewazTpoybf Version 1.0 - Cinematic Video Storyboard",
        "**Target Video Duration:** 11 Minutes 30 Seconds (8-12 Min Target Window)  ",
        "**Resolution:** 4K UHD (3840x2160) @ 60 FPS / Mobile 1080x1920 (9:16)  ",
        "**Style:** Modern, Clean, High-Tech Forensic, Cinematic, Easy-to-Follow  ",
        "**Target Audience:** Landowners, Surveyors, Lawyers, Researchers, Record Keepers  ",
        "\n---\n"
    ]

    pdf = SimplePdfWriter(title="LR-Newaz Storyboard")
    pdf.add_title("LR-Newaz Version 1.0", "Complete Cinematic Tutorial Storyboard (14 Scenes)")

    for sc in SCENES:
        md_lines.append(f"## Scene {sc['num']:02d}: {sc['title']}")
        md_lines.append(f"- **Timecode:** `{sc['time']}`")
        md_lines.append(f"- **Screen Action:** {sc['screen_action']}")
        md_lines.append(f"- **Camera Movement:** {sc['camera']}")
        md_lines.append(f"- **Transition:** {sc['transition']}")
        md_lines.append(f"- **On-Screen Text (Lower Third):** *\"{sc['text_overlay']}\"*")
        md_lines.append(f"- **English Narration:** {sc['narration_en']}")
        md_lines.append(f"- **Bangla Narration:** {sc['narration_bn']}")
        md_lines.append(f"- **UI Elements & TestTags:** `{sc['ui_elements']}`")
        md_lines.append("\n---\n")

        pdf.add_heading1(f"Scene {sc['num']:02d}: {sc['title']}")
        pdf.add_table_row("Timecode", sc['time'], "Transition", sc['transition'][:18], is_header=True)
        pdf.add_bullet("Screen Action:", sc['screen_action'])
        pdf.add_bullet("Camera Movement:", sc['camera'])
        pdf.add_bullet("On-Screen Text:", sc['text_overlay'])
        pdf.add_bullet("Narration (EN):", sc['narration_en'])
        pdf.add_bullet("UI Components:", sc['ui_elements'])
        pdf.y -= 6

    md_path = os.path.join(TUTORIAL_DIR, "Storyboard.md")
    with open(md_path, "w", encoding="utf-8") as f:
        f.write("\n".join(md_lines))

    pdf_path = os.path.join(TUTORIAL_DIR, "Storyboard.pdf")
    pdf.write_to_file(pdf_path)


# ----------------------------------------------------------------------
# 2. Voiceover Scripts Generator (EN & BN: DOCX & MD)
# ----------------------------------------------------------------------
def build_voiceovers():
    en_sections = []
    en_md = [
        "# LR-NewazTpoybf Version 1.0 - English Voice-over Script",
        "**Tone:** Confident, Authoritative, Professional, Warm, Beginner-Friendly  ",
        "**Accent / Delivery:** International Standard English, Clear Articulation  ",
        "**Total Duration:** ~11 Minutes 30 Seconds  ",
        "\n---\n"
    ]
    for sc in SCENES:
        heading = f"Scene {sc['num']:02d} ({sc['time']}): {sc['title']}"
        paras = [
            ("Visual Cue", sc['screen_action']),
            ("Spoken Voiceover", sc['narration_en']),
            ("Pronunciation Guide", "Khatian (Kha-ti-aan), Dag (Daag), Mouza (Mow-zah), Warishnama (Wa-rish-na-mah)")
        ]
        en_sections.append((heading, paras))

        en_md.append(f"### Scene {sc['num']:02d}: {sc['title']} (`{sc['time']}`)")
        en_md.append(f"> **Action:** {sc['screen_action']}\n")
        en_md.append(f"**Narration:**\n\"{sc['narration_en']}\"\n")
        en_md.append("---\n")

    generate_docx(os.path.join(TUTORIAL_DIR, "VoiceOver_EN.docx"), "LR-Newaz v1.0 - English Voiceover Script", en_sections)
    with open(os.path.join(TUTORIAL_DIR, "VoiceOver_EN.md"), "w", encoding="utf-8") as f:
        f.write("\n".join(en_md))

    bn_sections = []
    bn_md = [
        "# এলআর-নেওয়াজ ভার্সন ১.০ - বাংলা ভয়েস-ওভার স্ক্রিপ্ট (VoiceOver_BN)",
        "**কণ্ঠস্বর:** প্রাতিষ্ঠানিক, সাবলীল, আত্মবিশ্বাসী এবং শিক্ষামূলক প্রমিত বাংলা  ",
        "**টার্গেট সময়কাল:** ১১ মিনিট ৩০ সেকেন্ড  ",
        "**আইনি পরিভাষা:** খতিয়ান, দাগ নম্বর, মৌজা নকশা, ওয়ারিশনামা, হিস্যা বণ্টন, পর্চা  ",
        "\n---\n"
    ]
    for sc in SCENES:
        heading = f"দৃশ্য {sc['num']:02d} ({sc['time']}): {sc['title']}"
        paras = [
            ("স্ক্রিন অ্যাকশন", sc['screen_action']),
            ("কথোপকথন (ভয়েস-ওভার)", sc['narration_bn']),
            ("পরামর্শ", "উচ্চারণ স্পষ্ট এবং প্রতিটি ক্লিকের সাথে তাল মিলিয়ে ধীরে বলতে হবে।")
        ]
        bn_sections.append((heading, paras))

        bn_md.append(f"### দৃশ্য {sc['num']:02d}: {sc['title']} (`{sc['time']}`)")
        bn_md.append(f"> **স্ক্রিন অ্যাকশন:** {sc['screen_action']}\n")
        bn_md.append(f"**ভয়েস-ওভার (বাংলা):**\n\"{sc['narration_bn']}\"\n")
        bn_md.append("---\n")

    generate_docx(os.path.join(TUTORIAL_DIR, "VoiceOver_BN.docx"), "LR-Newaz v1.0 - বাংলা ভয়েস-ওভার স্ক্রিপ্ট", bn_sections)
    with open(os.path.join(TUTORIAL_DIR, "VoiceOver_BN.md"), "w", encoding="utf-8") as f:
        f.write("\n".join(bn_md))


# ----------------------------------------------------------------------
# 3. Screen Recording Checklist (PDF & MD)
# ----------------------------------------------------------------------
def build_recording_checklist():
    pdf = SimplePdfWriter(title="LR-Newaz Recording Checklist")
    pdf.add_title("LR-Newaz Version 1.0", "Screen Recording Plan & UI Interaction Checklist")

    md = [
        "# LR-NewazTpoybf Version 1.0 - Production Screen Recording Plan",
        "This checklist provides the exact sequence of clicks, UI testTags, gestures, and inputs for screen capture.",
        "\n---\n"
    ]

    steps = [
        ("Step 01: Setup & Emulator Specs", "Resolution 1080x2400 (or 4K), 60 FPS, Dark/Light theme set to Light Parchment, touch feedback enabled (show taps)."),
        ("Step 02: APK Installation", "Capture file download from release directory, Package Installer launch, 'Install' tap, and home screen icon launch."),
        ("Step 03: Tesseract Language Setup", "Tap AppBar 'tesseract_setup_button', show Bengali (ben) & English (eng) pack cards, tap verify status."),
        ("Step 04: Dashboard Exploration", "Scroll through LegalDisclaimerBanner, highlight MetricCard numbers (Total, Verified, Errors), tap Quick CSV bar."),
        ("Step 05: Bulk SAF Upload", "Tap 'fab_saf_folder', select sample deed directory, record animated progress indicators, hash computation, zero-byte skip."),
        ("Step 06: Document OCR & Binarization", "Open DocumentDetailScreen, demonstrate Otsu binarization slider, toggle between Raw Scan and High-Contrast Text."),
        ("Step 07: Bangla HCR Review", "Tap 'btn_open_hcr' banner, open HcrCorrectionScreen, inspect token confidence chips, edit one uncertain token, save."),
        ("Step 08: Cadastral Map GIS Detection", "Load survey sheet, show contour vector polygon extraction, Bengali digit bounding boxes, road and water layers."),
        ("Step 09: Warishnama Inheritance Matching", "Tap 'btn_open_warish_matcher', choose Warish certificate, view heir tree, verify statutory Ana-Ganda-Kranti shares."),
        ("Step 10: Cadastral Plot Highlighting", "Tap on heir share 'দাগ ৪০২', demonstrate instant emerald green polygon highlight and gold perimeter glow."),
        ("Step 11: Historical Property Atlas", "Tap 'card_atlas_banner', scrub CS -> SA -> RS -> BS timeline, observe Dag 402 split into 402/1 and 402/2."),
        ("Step 12: Offline FTS Search", "Type 'দাগ ৪০২' into 'search_input', demonstrate 15ms instantaneous query filtering on HomeScreen."),
        ("Step 13: Report Export Suite", "Tap 'export_csv_button', launch ReportExportDialog, generate PDF Dossier, Excel XLSX, and ZIP archive."),
        ("Step 14: Final Security Summary", "Show Settings, zero internet traffic in network profiler, Room database integrity confirmation.")
    ]

    pdf.add_heading1("Studio Production Recording Checklist")
    pdf.add_paragraph("Follow these exact steps during production screen capture to match the master narration:")

    for title, desc in steps:
        pdf.add_bullet(title + ":", desc)
        md.append(f"### {title}\n- **Instructions:** {desc}\n")

    pdf.write_to_file(os.path.join(TUTORIAL_DIR, "Recording_Checklist.pdf"))
    with open(os.path.join(TUTORIAL_DIR, "Recording_Checklist.md"), "w", encoding="utf-8") as f:
        f.write("\n".join(md))


# ----------------------------------------------------------------------
# 4. Master Tutorial Script & AI Demonstrations (PDF & MD)
# ----------------------------------------------------------------------
def build_tutorial_script():
    pdf = SimplePdfWriter(title="LR-Newaz Tutorial Master Script")
    pdf.add_title("LR-Newaz Master Tutorial Script", "Director's Cut: Camera Angles, Split-Screen VFX & Before/After Demos")

    md = [
        "# LR-NewazTpoybf Version 1.0 - Master Tutorial Script & VFX Guide",
        "Includes camera movements, split-screen before/after AI comparisons, cursor highlights, and animated callouts.",
        "\n---\n"
    ]

    pdf.add_heading1("Director's Cinematic Specifications")
    pdf.add_paragraph("Master reference for video editors, compositors, and AI video generators (Veo 3 / Gemini Video).")

    vfx_demos = [
        ("AI Demo 1: Historical Scan to Clean OCR", "Cinematic vertical split wipe. Left side shows 100-year-old faded yellowed parchment deed with ink stains. Right side displays Otsu adaptive binarized high-contrast text with recognized digital Bengali typography and 98% confidence badges."),
        ("AI Demo 2: Cursive Bengali Handwriting (HCR)", "Split-screen macro zoom. Left side features degraded cursive ink notes from land registrar margin. Right side highlights individual token chips: 'মং দশ আনা' (Verified, 99.4%) with interactive correction tooltip."),
        ("AI Demo 3: Cadastral Survey Sheet to Emerald Polygon", "Holographic 3D elevation wipe. The flat 2D British-era survey map transforms into an interactive GIS vector canvas. Target parcel Dag 402 pulses in radiant emerald green with golden boundary vertices.")
    ]

    pdf.add_heading2("Before / After AI Demonstrations")
    for title, desc in vfx_demos:
        pdf.add_bullet(title + ":", desc)
        md.append(f"### {title}\n{desc}\n\n---\n")

    pdf.add_heading2("Animated Callouts & VFX Guide")
    callouts = [
        ("Touch Tap Indicator", "Translucent white ripple expanding to 48dp with 300ms fade-out upon every user click."),
        ("Focus Glow Box", "Rounded amber/emerald border (#F59E0B / #10B981) highlighting active buttons with 2px stroke."),
        ("Laser Scanning Line", "Cyan horizontal laser beam scanning across documents during OCR & HCR processing."),
        ("Success Checkmark Stamp", "Gold foil holographic badge bursting with soft sparkles upon export completion.")
    ]
    for c_title, c_desc in callouts:
        pdf.add_bullet(c_title + ":", c_desc)
        md.append(f"**{c_title}:** {c_desc}\n")

    pdf.write_to_file(os.path.join(TUTORIAL_DIR, "Tutorial_Script.pdf"))
    with open(os.path.join(TUTORIAL_DIR, "Tutorial_Script.md"), "w", encoding="utf-8") as f:
        f.write("\n".join(md))


# ----------------------------------------------------------------------
# 5. Caption Files (English.srt & Bangla.srt)
# ----------------------------------------------------------------------
def build_subtitles():
    timestamps = [
        ("00:00:00,000", "00:00:35,000"),
        ("00:00:35,000", "00:01:15,000"),
        ("00:01:15,000", "00:02:00,000"),
        ("00:02:00,000", "00:02:40,000"),
        ("00:02:40,000", "00:03:30,000"),
        ("00:03:30,000", "00:04:20,000"),
        ("00:04:20,000", "00:05:15,000"),
        ("00:05:15,000", "00:06:10,000"),
        ("00:06:10,000", "00:07:15,000"),
        ("00:07:15,000", "00:08:05,000"),
        ("00:08:05,000", "00:09:05,000"),
        ("00:09:05,000", "00:09:50,000"),
        ("00:09:50,000", "00:10:45,000"),
        ("00:10:45,000", "00:11:30,000")
    ]

    en_srt = []
    bn_srt = []

    for i, sc in enumerate(SCENES):
        start_t, end_t = timestamps[i]
        
        en_srt.append(f"{i + 1}")
        en_srt.append(f"{start_t} --> {end_t}")
        en_srt.append(sc['narration_en'])
        en_srt.append("")

        bn_srt.append(f"{i + 1}")
        bn_srt.append(f"{start_t} --> {end_t}")
        bn_srt.append(sc['narration_bn'])
        bn_srt.append("")

    with open(os.path.join(TUTORIAL_DIR, "English.srt"), "w", encoding="utf-8") as f:
        f.write("\n".join(en_srt))

    with open(os.path.join(TUTORIAL_DIR, "Bangla.srt"), "w", encoding="utf-8") as f:
        f.write("\n".join(bn_srt))

    print(f"Generated SRT Subtitles: English.srt & Bangla.srt ({len(SCENES)} subtitles each)")


# ----------------------------------------------------------------------
# 6. AI Video Master Prompts (Veo 3 & Gemini Video)
# ----------------------------------------------------------------------
def build_ai_prompts():
    veo3_prompt = """// ==============================================================================
// GOOGLE VEO 3 MASTER PROMPT: LR-NEWAZ VERSION 1.0 CINEMATIC USER TUTORIAL
// ==============================================================================
// Output Spec: 4K UHD (3840x2160), 60 FPS, Master Cinematic Software Showcase
// Color Palette: Deep Sapphire Blue (#0B132B), Antique Land Parchment (#FAF6EB),
//                Emerald Green Dag Highlight (#10B981), Regal Gold Accent (#F59E0B)
// Lighting: High-tech soft studio key light with volumetric rim lighting on smartphone device

PROMPT:
Cinematic 4K 60fps software showcase video for "LR-Newaz Bangladesh Land Record Extractor v1.0". 
The video opens with a hyper-detailed 3D macro shot of a sleek modern Android smartphone resting on a minimalist walnut architect desk. Beside the phone are authentic historical Bangladesh land deeds (CS, SA, RS Khatians) with crimson revenue stamps. 

Camera performs an ultra-smooth floating crane swoop into the smartphone screen, showcasing the high-contrast Material Design 3 interface. The UI features warm parchment-textured cards and sapphire buttons with Bengali and English typography. 

Sequence of actions:
1. The user's hand taps 'Input Folder (SAF)' Floating Action Button with a gentle, glowing tactile ripple. The screen transitions into the bulk document queue with real-time cryptographic hash verifications.
2. Split-screen transformation: A weathered, 100-year-old cursive Bengali deed is scanned by a horizontal neon-cyan laser sweep, dynamically resolving into crisp, digitized tabular Khatian text with emerald green confidence tags.
3. Bangla Handwriting Recognition (HCR): Macro zoom on handwritten ink marginalia, where words like 'মং দশ আনা' are highlighted by amber bounding boxes and corrected via an elegant touch keypad.
4. Cadastral Survey Map Intelligence: The camera pans across a vintage British-era survey map. Contours lock into glowing vector polygons. Dag plot #402 illuminates in vivid radiant emerald green with an animated golden boundary halo.
5. Warishnama inheritance matching: A genealogical tree cascades smoothly downwards, connecting deceased owners to lawful heirs with computed statutory shares.
6. Multi-format export: A final tap on 'Export Report' generates a pristine multi-page court dossier PDF with authentic official stamps, accompanied by a soft golden particle celebration.

Camera physics: Butter-smooth gimbal movements, gentle rack focus between foreground phone glass and background land deeds, zero motion judder, 4K crisp textures, photorealistic reflections on Gorilla Glass, premium corporate launch aesthetic.
"""

    gemini_prompt = """// ==============================================================================
// GEMINI VIDEO MASTER PROMPT: LR-NEWAZTPoybf v1.0 PRODUCTION TUTORIAL
// ==============================================================================
Generate an 11-minute comprehensive cinematic software walkthrough for LR-NewazTpoybf Version 1.0 (Bangladesh Land Record Intelligence).

CORE VISUAL THEME:
- Modern Android Material 3 with Parchment Paper and Deep Sapphire styling.
- High-contrast visual representations of aged Bengali land deeds (CS, SA, RS, BS Porcha).
- Emerald green (#10B981) used strictly for verified Dag parcel plots and inheritance entitlements.
- Amber (#F59E0B) for heuristic warnings and record gaps.

SCENE COMPOSITING RULES:
- Frame Rate: 60 FPS constant.
- Screen Actions: Strictly synchronize touch interactions with button testTags ('btn_open_warish_matcher', 'fab_saf_folder', 'btn_open_hcr', 'card_atlas_banner').
- Text Overlays: Clean modern sans-serif lower-thirds in English with Bengali subtitles.
- Audio Bed: Subtle ambient corporate tech synth bed with dynamic sidechain ducking during spoken narration.
"""

    with open(os.path.join(TUTORIAL_DIR, "Veo3_MasterPrompt.txt"), "w", encoding="utf-8") as f:
        f.write(veo3_prompt)

    with open(os.path.join(TUTORIAL_DIR, "GeminiVideo_MasterPrompt.txt"), "w", encoding="utf-8") as f:
        f.write(gemini_prompt)

    print("Generated Master AI Prompts: Veo3_MasterPrompt.txt & GeminiVideo_MasterPrompt.txt")


# ----------------------------------------------------------------------
# 7. Music, Sound Effects & Export Guides (PDF & MD)
# ----------------------------------------------------------------------
def build_guides():
    music_pdf = SimplePdfWriter(title="LR-Newaz Music Guide")
    music_pdf.add_title("LR-Newaz Tutorial Music Guide", "Acoustic & Electronic Background Music Direction")
    music_pdf.add_heading1("Soundtrack Style & Progression")
    music_pdf.add_paragraph("Style: Modern Corporate Technology, Soft Ambient Electronica, Non-Distracting Minimalist Beats.")
    music_pdf.add_bullet("Intro (00:00 - 01:15):", "Warm piano chords with subtle synthetic pad swell. Tempo: 110 BPM. Energy: Inspiring, Welcoming.")
    music_pdf.add_bullet("Technical Workflow (01:15 - 06:10):", "Light electronic pulses, rhythmic marimba and warm bassline. Sidechain ducked by -14 dB under voiceover.")
    music_pdf.add_bullet("AI Highlights & Atlas (06:10 - 09:50):", "Upbeat tech groove with higher harmonic frequencies highlighting AI intelligence and emerald parcel snapping.")
    music_pdf.add_bullet("Outro & Credits (09:50 - 11:30):", "Confident, resolving orchestral-electronic crescendo ending on a warm sustained gold chord.")
    music_pdf.write_to_file(os.path.join(TUTORIAL_DIR, "Music_Guide.pdf"))

    music_md = """# LR-Newaz Version 1.0 - Tutorial Background Music Guide
- **Genre:** Ambient Technology / Modern Corporate Neo-Classical
- **Recommended Tracks:** 'Digital Pioneers', 'Forensic Clarity', 'Minimal Tech Horizon'
- **BPM:** 105 - 115 BPM
- **Ducking Settings:** -14 dB attenuation during voiceover, 400ms attack, 800ms release.
"""
    with open(os.path.join(TUTORIAL_DIR, "Music_Guide.md"), "w", encoding="utf-8") as f:
        f.write(music_md)

    sfx_pdf = SimplePdfWriter(title="LR-Newaz SFX Guide")
    sfx_pdf.add_title("LR-Newaz Sound Effects Guide", "Foley & Audio Feedback Cue Sheet")
    sfx_pdf.add_heading1("Subtle Professional Audio Cues")
    sfx_cues = [
        ("Screen Tap", "Subtle dry wooden thud (soft tactile feedback, -20 dB)."),
        ("Scan Laser Sweep", "Smooth low-pass sci-fi frequency riser sweeping 400Hz to 1200Hz."),
        ("OCR / HCR Verified", "Gentle crystalline chime in C-major (indicating 98%+ confidence)."),
        ("Dag Highlight Snap", "Resonant magnetic snap with soft sub-bass punch as Dag 402 turns emerald green."),
        ("Export Completed", "Delicate metallic acoustic harp chord signaling dossier generation.")
    ]
    for s_name, s_desc in sfx_cues:
        sfx_pdf.add_bullet(s_name + ":", s_desc)
    sfx_pdf.write_to_file(os.path.join(TUTORIAL_DIR, "Sound_Effects_Guide.pdf"))

    sfx_md = """# LR-Newaz Version 1.0 - Sound Effects (Foley) Guide
All sound effects must be clean, subtle, and mixed at -18 dB to -24 dB so as not to overwhelm narration.
"""
    with open(os.path.join(TUTORIAL_DIR, "Sound_Effects_Guide.md"), "w", encoding="utf-8") as f:
        f.write(sfx_md)

    exp_pdf = SimplePdfWriter(title="LR-Newaz Export Guide")
    exp_pdf.add_title("LR-Newaz Video Export Guide", "Master Multi-Platform Video Encoding & Publishing Specs")
    exp_pdf.add_heading1("Distribution Presets")
    exp_pdf.add_bullet("16:9 YouTube / Web Master:", "3840x2160 (4K UHD) or 1920x1080 (FHD), 60 FPS, Codec: H.265 / ProRes 422 or H.264 High Profile, Bitrate: 45 Mbps.")
    exp_pdf.add_bullet("9:16 Mobile / Shorts / TikTok:", "1080x1920 (Vertical), 60 FPS, Centered zoom on smartphone UI with animated subtitles in lower-third.")
    exp_pdf.add_bullet("1:1 LinkedIn / Social Feed:", "1080x1080 (Square), 30/60 FPS, High-contrast frame with bold headline banner and burned-in captions.")
    exp_pdf.write_to_file(os.path.join(TUTORIAL_DIR, "Export_Guide.pdf"))

    exp_md = """# LR-Newaz Version 1.0 - Video Export & Distribution Guide
- **YouTube 4K Master (16:9):** 3840x2160, 60fps, 45 Mbps, Rec.709 color space.
- **Shorts / TikTok (9:16):** 1080x1920, 60fps, 18 Mbps.
- **LinkedIn / Instagram (1:1):** 1080x1080, 30fps, 12 Mbps.
"""
    with open(os.path.join(TUTORIAL_DIR, "Export_Guide.md"), "w", encoding="utf-8") as f:
        f.write(exp_md)


# ----------------------------------------------------------------------
# 8. Master Tutorial README & Index
# ----------------------------------------------------------------------
def build_readme():
    readme_content = """# LR-NewazTpoybf Version 1.0 - Complete Tutorial Video Package

Welcome to the official, production-ready cinematic user tutorial package for **LR-Newaz Version 1.0** (Bangladesh Land Record Extractor & Forensic Intelligence Platform).

Every deliverable in this package has been generated automatically to reflect the exact final user interface, navigation routes, buttons, and workflows of the application.

---

## Deliverables Index

| Deliverable | File Format | Description |
|---|---|---|
| **Video Storyboard** | `Storyboard.pdf` & `Storyboard.md` | Scene-by-scene 14-scene cinematic storyboard (11m 30s) with camera, action, and text cues. |
| **English Voiceover** | `VoiceOver_EN.docx` & `VoiceOver_EN.md` | Full professional English narration script with timing and pronunciation guides. |
| **Bangla Voiceover** | `VoiceOver_BN.docx` & `VoiceOver_BN.md` | প্রমিত বাংলা ভয়েস-ওভার স্ক্রিপ্ট ও আইনি পরিভাষা নির্দেশিকা। |
| **Master Tutorial Script** | `Tutorial_Script.pdf` & `Tutorial_Script.md` | Director's cut with before/after AI demonstrations, split-screen VFX, and animated callouts. |
| **Screen Recording Checklist**| `Recording_Checklist.pdf` & `Recording_Checklist.md` | Step-by-step recording plan with exact UI testTags and interaction steps. |
| **English Subtitles** | `English.srt` | Standard SubRip caption file timed perfectly to master scenes. |
| **Bangla Subtitles** | `Bangla.srt` | বাংলা সাবটাইটেল ফাইল (SRT)। |
| **YouTube Thumbnail** | `Thumbnail.png` | 1920x1080 high-contrast cinematic thumbnail with smartphone mockup, Cadastral map, and gold badges. |
| **Veo 3 AI Prompt** | `Veo3_MasterPrompt.txt` | Master text prompt for Google Veo 3 video generation. |
| **Gemini Video Prompt**| `GeminiVideo_MasterPrompt.txt` | Comprehensive AI video generation directives. |
| **Background Music Guide** | `Music_Guide.pdf` & `Music_Guide.md` | Acoustic & electronic soundtrack guide with audio ducking specifications. |
| **Sound Effects Guide** | `Sound_Effects_Guide.pdf` & `Sound_Effects_Guide.md` | Foley & sound design cue sheet for UI clicks, scans, and export chimes. |
| **Export Instructions** | `Export_Guide.pdf` & `Export_Guide.md` | Multi-format rendering specs for YouTube 4K (16:9), TikTok (9:16), and LinkedIn (1:1). |

---

## 14-Scene Feature Demonstration Roadmap

1. **Scene 01 (00:00 - 00:35):** Cinematic Introduction & Brand Identity
2. **Scene 02 (00:35 - 01:15):** Standalone APK Installation & Zero-Permission Privacy
3. **Scene 03 (01:15 - 02:00):** Language Selection & Tesseract Offline OCR Setup
4. **Scene 04 (02:00 - 02:40):** Home Dashboard, Forensic Metrics & Legal Disclaimer
5. **Scene 05 (02:40 - 03:30):** Bulk Document Ingestion & Storage Access Framework (SAF)
6. **Scene 06 (03:30 - 04:20):** High-Contrast Document OCR & Adaptive Binarization
7. **Scene 07 (04:20 - 05:15):** Module 04: Bangla Handwriting Recognition (HCR) & Marginalia Review
8. **Scene 08 (05:15 - 06:10):** Module 05: Cadastral Survey Map Intelligence & Polygon Detection
9. **Scene 09 (06:10 - 07:15):** Module 06: Warishnama Inheritance Matching & Genealogical Links
10. **Scene 10 (07:15 - 08:05):** Cadastral Dag Highlighting & Interactive Parcel Inspection (Dag 402)
11. **Scene 11 (08:05 - 09:05):** Module 13 & 14: Historical Property Atlas & Forensic Validation
12. **Scene 12 (09:05 - 09:50):** High-Speed Offline Search & Multi-Criteria Filtering
13. **Scene 13 (09:50 - 10:45):** Multi-Format Forensic Report Export (PDF, Excel, GeoJSON, ZIP)
14. **Scene 14 (10:45 - 11:30):** Offline Security, Summary & Next Steps

---

## Standalone APK Artifact
- Location: `app-debug.apk` (and `public/app-debug.apk`)
- Size: ~26 MB
- Compatibility: Android 8.0+ (API 26+)
"""
    with open(os.path.join(TUTORIAL_DIR, "README.md"), "w", encoding="utf-8") as f:
        f.write(readme_content)


# ----------------------------------------------------------------------
# Main Execution
# ----------------------------------------------------------------------
def main():
    print("Building LR-NewazTpoybf Version 1.0 Cinematic Tutorial Package...")
    build_storyboard()
    build_voiceovers()
    build_recording_checklist()
    build_tutorial_script()
    build_subtitles()
    build_ai_prompts()
    build_guides()
    build_readme()

    thumbnail_path = os.path.join(TUTORIAL_DIR, "Thumbnail.png")
    generate_thumbnail_png(thumbnail_path)

    for fname in os.listdir(TUTORIAL_DIR):
        src = os.path.join(TUTORIAL_DIR, fname)
        dst = os.path.join(PUBLIC_TUTORIAL_DIR, fname)
        if os.path.isfile(src):
            with open(src, "rb") as sf, open(dst, "wb") as df:
                df.write(sf.read())

    print("\nAll tutorial deliverables successfully created in /tutorial and mirrored to /public/tutorial!")

if __name__ == "__main__":
    main()
