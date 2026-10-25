import os
import math
import random
from PIL import Image, ImageDraw, ImageFont

WIDTH = 800
HEIGHT = 450
TOTAL_FRAMES = 80  # 80 frames at 250ms = 20.0 seconds!
FRAME_DURATION = 250  # 250 ms per frame

output_dir = os.path.join(os.path.dirname(__file__))
gif_path = os.path.join(output_dir, "astron-showcase.gif")
poster_path = os.path.join(output_dir, "brag.jpg")

# Load fonts
fonts_dir = os.path.join(os.environ.get("WINDIR", "C:\\Windows"), "Fonts")
def get_font(name, size):
    path = os.path.join(fonts_dir, name)
    if os.path.exists(path):
        return ImageFont.truetype(path, size)
    return ImageFont.load_default()

font_title_giant = get_font("segoeuib.ttf", 38)
font_title_large = get_font("segoeuib.ttf", 28)
font_heading = get_font("segoeuib.ttf", 22)
font_body = get_font("segoeuib.ttf", 16)
font_mono = get_font("consolab.ttf", 16)
font_mono_small = get_font("consolab.ttf", 13)
font_mono_giant = get_font("consolab.ttf", 44)

# Load Logo
logo_img = None
logo_path = os.path.join(os.path.dirname(output_dir), "Logo.png")
if os.path.exists(logo_path):
    try:
        raw_logo = Image.open(logo_path).convert("RGBA")
        logo_img = raw_logo.resize((140, 140), Image.Resampling.LANCZOS)
    except Exception as e:
        print("Logo load error:", e)

# Pre-generate persistent starfield
random.seed(42)
stars = []
for _ in range(120):
    stars.append((random.randint(0, WIDTH), random.randint(0, HEIGHT), random.uniform(0.5, 2.5), random.uniform(0.2, 1.0)))

frames = []

def draw_background(draw, frame_idx):
    # Deep cosmic dark gradient
    draw.rectangle([(0, 0), (WIDTH, HEIGHT)], fill=(7, 10, 18))
    
    # Subtle nebula glow
    nebula_x = int(WIDTH * 0.5 + 40 * math.sin(frame_idx * 0.1))
    nebula_y = int(HEIGHT * 0.5 + 25 * math.cos(frame_idx * 0.1))
    for r in range(160, 40, -30):
        alpha = int(12 * (1 - r / 160))
        draw.ellipse([(nebula_x - r, nebula_y - r), (nebula_x + r, nebula_y + r)], outline=(37, 99, 235, alpha), width=3)
    
    # Twinkling stars
    for sx, sy, size, base_alpha in stars:
        twinkle = (math.sin(frame_idx * 0.2 + sx) + 1) * 0.5
        alpha = int(255 * (0.3 + 0.7 * twinkle * base_alpha))
        color = (200, 225, 255) if size > 1.5 else (140, 170, 210)
        draw.ellipse([(sx, sy), (sx + size, sy + size)], fill=color)

def draw_top_hud(draw, scene_name, progress_pct):
    # Top status bar
    draw.rectangle([(0, 0), (WIDTH, 36)], fill=(14, 20, 36))
    draw.line([(0, 36), (WIDTH, 36)], fill=(37, 99, 235), width=1)
    
    draw.text((20, 9), "🪐 ASTRON v1.0.0", fill=(59, 130, 246), font=font_mono)
    draw.text((WIDTH // 2 - 80, 9), f"SCENE: {scene_name}", fill=(160, 185, 220), font=font_mono_small)
    
    # Progress mini-bar
    bar_w = 120
    draw.rectangle([(WIDTH - bar_w - 20, 12), (WIDTH - 20, 24)], outline=(50, 75, 110), width=1)
    fill_w = int(bar_w * (progress_pct / 100.0))
    if fill_w > 0:
        draw.rectangle([(WIDTH - bar_w - 20, 12), (WIDTH - bar_w - 20 + fill_w, 24)], fill=(16, 185, 129))
    draw.text((WIDTH - bar_w - 65, 10), f"{int(progress_pct)}%", fill=(16, 185, 129), font=font_mono_small)

# -------------------------------------------------------------
# Generate 80 Frames (20 seconds @ 4 FPS / 250ms per frame)
# -------------------------------------------------------------
for f in range(TOTAL_FRAMES):
    img = Image.new("RGB", (WIDTH, HEIGHT), (7, 10, 18))
    draw = ImageDraw.Draw(img)
    draw_background(draw, f)
    
    time_sec = f * 0.25
    progress = (f / float(TOTAL_FRAMES)) * 100.0
    
    # =========================================================
    # SCENE 1: The Hook (0.0s - 2.5s -> frames 0 to 9)
    # =========================================================
    if f < 10:
        draw_top_hud(draw, "THE HOOK", (f / 10.0) * 12.0)
        
        # Glitch warning box
        box_y = 90
        draw.rounded_rectangle([(140, box_y), (WIDTH - 140, box_y + 80)], radius=12, fill=(28, 16, 24), outline=(239, 68, 68), width=2)
        draw.text((160, box_y + 16), "⚠️  AirDrop: Peer Not Found (Error -42)", fill=(248, 113, 113), font=font_mono)
        draw.text((160, box_y + 44), "⚠️  Quick Share: Connection Timed Out", fill=(251, 191, 36), font=font_mono)
        
        # Big Punchy Text
        draw.text((WIDTH // 2 - 180, 210), "TRANSFER FAILED?", fill=(255, 255, 255), font=font_title_giant)
        draw.text((WIDTH // 2 - 195, 270), "NOT IN THIS GALAXY.", fill=(59, 130, 246), font=font_title_large)
        
        # Subtle subtext
        draw.text((WIDTH // 2 - 170, 340), "Enter the 10/10 God-Tier Transfer Engine", fill=(148, 163, 184), font=font_body)

    # =========================================================
    # SCENE 2: The Reveal (2.5s - 6.0s -> frames 10 to 23)
    # =========================================================
    elif f < 24:
        draw_top_hud(draw, "THE REVEAL", 12.0 + ((f - 10) / 14.0) * 18.0)
        
        # Paste Logo in center
        if logo_img:
            logo_x = WIDTH // 2 - 70
            logo_y = 80
            # Draw glowing halo around logo
            draw.ellipse([(logo_x - 15, logo_y - 15), (logo_x + 155, logo_y + 155)], outline=(37, 99, 235), width=3)
            img.paste(logo_img, (logo_x, logo_y), logo_img if logo_img.mode == "RGBA" else None)
        
        draw.text((WIDTH // 2 - 130, 240), "🪐 ASTRON", fill=(255, 255, 255), font=font_title_giant)
        draw.text((WIDTH // 2 - 210, 300), "12 TRANSPORTS. ZERO EXCUSES.", fill=(124, 58, 237), font=font_heading)
        draw.text((WIDTH // 2 - 240, 345), "Whatever the file. Whatever the network. Just send it.", fill=(59, 130, 246), font=font_body)
        
        # Badge row
        draw.rounded_rectangle([(220, 385), (WIDTH - 220, 415)], radius=8, fill=(14, 20, 36), outline=(50, 75, 110), width=1)
        draw.text((235, 392), "Native Kotlin 2.0 • Jetpack Compose • Room WAL", fill=(200, 220, 250), font=font_mono_small)

    # =========================================================
    # SCENE 3: Raw Speed Flex (6.0s - 10.0s -> frames 24 to 39)
    # =========================================================
    elif f < 40:
        draw_top_hud(draw, "WI-FI 7 SPEED FLEX", 30.0 + ((f - 24) / 16.0) * 25.0)
        
        # Speed progress counter
        speed_t = (f - 24) / 15.0
        current_speed = int(45 + (250 - 45) * min(1.0, speed_t * 1.3))
        
        # Center Holographic Speedometer Gauge
        center_x = WIDTH // 2
        center_y = 175
        radius = 85
        
        # Outer decorative orbit ring
        draw.ellipse([(center_x - radius - 10, center_y - radius - 10), (center_x + radius + 10, center_y + radius + 10)], outline=(30, 45, 75), width=2)
        
        # Main plasma gauge arc (from 135 deg to 405 deg)
        start_angle = 135
        total_sweep = 270
        sweep_active = int(total_sweep * (current_speed / 250.0))
        
        # Background arc
        draw.arc([(center_x - radius, center_y - radius), (center_x + radius, center_y + radius)], start=135, end=405, fill=(40, 55, 85), width=12)
        # Active glowing arc
        draw.arc([(center_x - radius, center_y - radius), (center_x + radius, center_y + radius)], start=135, end=135 + sweep_active, fill=(37, 99, 235), width=12)
        
        # Inner speed readout
        draw.text((center_x - 70, center_y - 30), f"{current_speed}", fill=(255, 255, 255), font=font_mono_giant)
        draw.text((center_x - 30, center_y + 15), "MB/s", fill=(59, 130, 246), font=font_mono)
        
        # Live Sparkline graph below
        graph_y = 310
        draw.rounded_rectangle([(160, graph_y), (WIDTH - 160, graph_y + 80)], radius=10, fill=(14, 20, 36), outline=(37, 99, 235), width=1)
        draw.text((180, graph_y + 10), "⚡ LIVE TELEMETRY: WI-FI 7 MLO (320 MHz)", fill=(245, 158, 11), font=font_mono_small)
        draw.text((180, graph_y + 32), "PEAK: 250.0 MB/s   AVG: 224.6 MB/s   ETA: 1.2s", fill=(148, 163, 184), font=font_mono_small)
        
        # Draw dynamic sparkline wave
        spark_pts = []
        for x_step in range(0, 440, 20):
            pt_x = 180 + x_step
            wave_val = math.sin((f * 0.4) + (x_step * 0.05)) * 10
            pt_y = graph_y + 65 - int(min(20, (x_step / 440.0) * 18 + wave_val))
            spark_pts.append((pt_x, pt_y))
        for i in range(len(spark_pts) - 1):
            draw.line([spark_pts[i], spark_pts[i+1]], fill=(16, 185, 129), width=2)
            
        draw.text((WIDTH // 2 - 165, 405), "Transferred 100 GB with < 35 MB RAM", fill=(200, 220, 250), font=font_body)

    # =========================================================
    # SCENE 4: 12-Transport Cascade (10.0s - 14.0s -> frames 40 to 55)
    # =========================================================
    elif f < 56:
        draw_top_hud(draw, "12 TRANSPORTS UNMATCHED", 55.0 + ((f - 40) / 16.0) * 20.0)
        
        draw.text((WIDTH // 2 - 210, 55), "WHATEVER THE NETWORK. JUST SEND IT.", fill=(255, 255, 255), font=font_heading)
        
        transports = [
            ("⚡ Wi-Fi 7 MLO (250 MB/s)", "Multi-link channel bonding", (37, 99, 235)),
            ("📡 Zero-Router SoftAP", "LocalOnlyHotspot auto-connect", (245, 158, 11)),
            ("📢 Wi-Fi Multicast + FEC", "1-to-Many with 0ms XOR recovery", (16, 185, 129)),
            ("🌐 Cross-Platform WebDrop", "SubtleCrypto E2EE for iOS/Mac/PC", (124, 58, 237)),
            ("✨ Optical Light Drop", "Air-gapped visual QR frames", (236, 72, 153)),
            ("🔌 High-Speed USB OTG", "Direct flash drive & SSD export", (14, 165, 233)),
        ]
        
        # Grid layout (2 columns x 3 rows)
        col_w = 280
        row_h = 75
        for idx, (title, subtitle, color) in enumerate(transports):
            col = idx % 2
            row = idx // 2
            bx = 100 + col * (col_w + 40)
            by = 105 + row * (row_h + 20)
            
            # Staggered entrance based on frame
            if f >= 40 + idx * 2:
                draw.rounded_rectangle([(bx, by), (bx + col_w, by + row_h)], radius=10, fill=(14, 20, 36), outline=color, width=2)
                draw.text((bx + 14, by + 14), title, fill=color, font=font_body)
                draw.text((bx + 14, by + 42), subtitle, fill=(148, 163, 184), font=font_mono_small)

    # =========================================================
    # SCENE 5: Zero-Router & Resumability (14.0s - 17.0s -> frames 56 to 67)
    # =========================================================
    elif f < 68:
        draw_top_hud(draw, "SEAMLESS FAILOVER & RESUME", 75.0 + ((f - 56) / 12.0) * 15.0)
        
        draw.text((WIDTH // 2 - 210, 60), "PULL THE ROUTER PLUG. ZERO LOSS.", fill=(255, 255, 255), font=font_heading)
        
        # Failover Visual Flow
        draw.rounded_rectangle([(140, 115), (320, 195)], radius=10, fill=(28, 16, 24), outline=(239, 68, 68), width=2)
        draw.text((155, 130), "Wi-Fi LAN Dropped", fill=(248, 113, 113), font=font_body)
        draw.text((155, 160), "Router offline", fill=(148, 163, 184), font=font_mono_small)
        
        # Arrow
        draw.text((360, 145), "➔", fill=(245, 158, 11), font=font_title_giant)
        
        # Failover target
        draw.rounded_rectangle([(420, 115), (WIDTH - 140, 195)], radius=10, fill=(16, 28, 22), outline=(16, 185, 129), width=2)
        draw.text((435, 130), "Auto-Failover Active", fill=(52, 211, 153), font=font_body)
        draw.text((435, 160), "Wi-Fi Direct P2P + BLE (0ms)", fill=(148, 163, 184), font=font_mono_small)
        
        # Crash Resumability Box
        draw.rounded_rectangle([(140, 235), (WIDTH - 140, 365)], radius=12, fill=(14, 20, 36), outline=(37, 99, 235), width=2)
        draw.text((165, 255), "♻️  Room SQLite WAL Crash Checkpoints", fill=(59, 130, 246), font=font_body)
        draw.text((165, 288), "• RandomAccessFile byte-level seek (`OP_FILE_RESUME = 0x09`)", fill=(203, 213, 225), font=font_mono_small)
        draw.text((165, 312), "• Process killed? Device rebooted? Resumes at exact byte.", fill=(203, 213, 225), font=font_mono_small)
        draw.text((165, 336), "• Zero re-download penalty. Zero corrupted files.", fill=(16, 185, 129), font=font_mono_small)

    # =========================================================
    # SCENE 6: Supernova Outro (17.0s - 20.0s -> frames 68 to 79)
    # =========================================================
    else:
        draw_top_hud(draw, "10/10 GOLD MASTER", 100.0)
        
        # Expanding supernova rings
        outro_f = f - 68
        center_x = WIDTH // 2
        center_y = 170
        for ring_idx in range(4):
            ring_r = int(50 + (outro_f * 14) + ring_idx * 30)
            if ring_r < 300:
                draw.ellipse([(center_x - ring_r, center_y - ring_r), (center_x + ring_r, center_y + ring_r)], outline=(16, 185, 129), width=2)
        
        # Success Badge Box
        draw.rounded_rectangle([(210, 85), (WIDTH - 210, 155)], radius=12, fill=(14, 30, 22), outline=(16, 185, 129), width=2)
        draw.text((230, 100), "✓ 100% SHA-256 VERIFIED", fill=(16, 185, 129), font=font_title_large)
        draw.text((230, 132), "Bit-for-bit cryptographic fidelity guaranteed", fill=(148, 163, 184), font=font_mono_small)
        
        # Giant Outro Lockup
        draw.text((WIDTH // 2 - 165, 200), "🪐 ASTRON", fill=(255, 255, 255), font=font_title_giant)
        draw.text((WIDTH // 2 - 215, 260), "10/10 GOLD MASTER STANDARD", fill=(245, 158, 11), font=font_heading)
        draw.text((WIDTH // 2 - 225, 305), "105/105 Unit & Integration Tests Passing", fill=(52, 211, 153), font=font_body)
        
        # Action Bar
        draw.rounded_rectangle([(180, 360), (WIDTH - 180, 410)], radius=10, fill=(37, 99, 235), outline=(59, 130, 246), width=1)
        draw.text((WIDTH // 2 - 160, 375), "GitHub: CodeSorcerer-007/Astron", fill=(255, 255, 255), font=font_body)
        
    frames.append(img)

# Save best poster frame (Frame 38: The peak 250 MB/s speed flex)
frames[38].save(poster_path, quality=95)
print(f"Saved poster to {poster_path}")

# Save full 20-second animated GIF (optimized palette)
frames[0].save(
    gif_path,
    save_all=True,
    append_images=frames[1:],
    duration=FRAME_DURATION,
    loop=0,
    optimize=True
)
print(f"Saved 20-second showcase GIF to {gif_path} ({len(frames)} frames)")
