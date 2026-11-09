import os
import math
import numpy as np
from PIL import Image, ImageDraw

def eval_cubic_bezier(p0, p1, p2, p3, num_pts=40):
    t = np.linspace(0, 1, num_pts)[:, None]
    pts = (1 - t)**3 * p0 + 3 * (1 - t)**2 * t * p1 + 3 * (1 - t) * t**2 * p2 + t**3 * p3
    return pts.tolist()

def create_lunify_icon(target_size, is_round=False):
    """
    Renders the official Lunify App Logo Icon:
    - Smokey Glass Obsidian Base (#0B0C10)
    - Smoky vapor plumes & diagonal glass sheen
    - Official Lunify Brand Note Logo from assets/logo.svg centered in adaptive safe zone
    """
    canvas_size = 1024
    scale = canvas_size / 108.0  # 108dp adaptive icon canvas
    
    # 1. Base Layer: Deep Smoky Obsidian Glass (#0B0C10)
    img = Image.new("RGBA", (canvas_size, canvas_size), (11, 12, 16, 255))
    
    # 2. Smoky Vapor Plume 1: Top-Left Diffuse Cloud
    smoke1_layer = Image.new("RGBA", (canvas_size, canvas_size), (0, 0, 0, 0))
    smoke1_draw = ImageDraw.Draw(smoke1_layer)
    cx1 = 36.0 * scale
    cy1 = 32.0 * scale
    r1_max = 60.0 * scale
    
    steps = 100
    for i in range(steps, 0, -1):
        r = (i / steps) * r1_max
        t = i / steps
        alpha = int(255 * 0.28 * ((1.0 - t) ** 1.5))
        # Silver slate smoke tint
        color = (226, 232, 240, alpha)
        smoke1_draw.ellipse([cx1 - r, cy1 - r, cx1 + r, cy1 + r], fill=color)
        
    img = Image.alpha_composite(img, smoke1_layer)
    
    # 3. Smoky Vapor Plume 2: Counter-Illumination Swirl (Bottom-Right)
    smoke2_layer = Image.new("RGBA", (canvas_size, canvas_size), (0, 0, 0, 0))
    smoke2_draw = ImageDraw.Draw(smoke2_layer)
    cx2 = 76.0 * scale
    cy2 = 74.0 * scale
    r2_max = 48.0 * scale
    
    for i in range(steps, 0, -1):
        r = (i / steps) * r2_max
        t = i / steps
        alpha = int(255 * 0.16 * ((1.0 - t) ** 1.4))
        color = (203, 213, 225, alpha)
        smoke2_draw.ellipse([cx2 - r, cy2 - r, cx2 + r, cy2 + r], fill=color)
        
    img = Image.alpha_composite(img, smoke2_layer)
    
    # 4. Diagonal Glass Sheen & Refraction Gradient
    sheen_layer = Image.new("RGBA", (canvas_size, canvas_size), (0, 0, 0, 0))
    sheen_draw = ImageDraw.Draw(sheen_layer)
    for y in range(canvas_size):
        # Diagonal factor
        diag = (y / canvas_size)
        alpha = int(45 * (1.0 - diag) + 12 * diag)
        sheen_draw.line([(0, y), (canvas_size, y)], fill=(255, 255, 255, alpha))
        
    img = Image.alpha_composite(img, sheen_layer)
    
    # 5. Foreground Logo: Render assets/logo.svg centered in 108dp canvas
    # Safe zone scale = 2.4, centered at (54, 54)
    # Original SVG viewport: 24x24, centered at (12, 12)
    def map_pt(x, y):
        cx = 54.0 + (x - 12.0) * 2.4
        cy = 54.0 + (y - 12.0) * 2.4
        return (cx * scale, cy * scale)

    logo_layer = Image.new("RGBA", (canvas_size, canvas_size), (0, 0, 0, 0))
    logo_draw = ImageDraw.Draw(logo_layer)
    
    # Path 1: Note Head Circle centered at (9, 18), radius 4.75
    head_c = map_pt(9, 18)
    head_r = 4.75 * 2.4 * scale
    logo_draw.ellipse(
        [head_c[0] - head_r, head_c[1] - head_r, head_c[0] + head_r, head_c[1] + head_r],
        fill=(255, 255, 255, 255),
    )
    
    # Path 2: Top Flag / Hook (Polygon from cubic beziers)
    hook_pts = []
    # Segment 1: (13, 1.25) to (13.75, 2)
    hook_pts.extend(eval_cubic_bezier(np.array([13, 1.25]), np.array([13.4142, 1.25]), np.array([13.75, 1.58579]), np.array([13.75, 2])))
    # Segment 2: to (19, 7.25)
    hook_pts.extend(eval_cubic_bezier(np.array([13.75, 2]), np.array([13.75, 4.8995]), np.array([16.1005, 7.25]), np.array([19, 7.25])))
    # Segment 3: to (19.75, 8)
    hook_pts.extend(eval_cubic_bezier(np.array([19, 7.25]), np.array([19.4142, 7.25]), np.array([19.75, 7.58579]), np.array([19.75, 8])))
    # Segment 4: to (19, 8.75)
    hook_pts.extend(eval_cubic_bezier(np.array([19.75, 8]), np.array([19.75, 8.41421]), np.array([19.4142, 8.75]), np.array([19, 8.75])))
    # Segment 5: to (12.25, 2)
    hook_pts.extend(eval_cubic_bezier(np.array([19, 8.75]), np.array([15.2721, 8.75]), np.array([12.25, 5.72792]), np.array([12.25, 2])))
    # Segment 6: to (13, 1.25)
    hook_pts.extend(eval_cubic_bezier(np.array([12.25, 2]), np.array([12.25, 1.58579]), np.array([12.5858, 1.25]), np.array([13, 1.25])))
    
    hook_screen_pts = [map_pt(p[0], p[1]) for p in hook_pts]
    logo_draw.polygon(hook_screen_pts, fill=(255, 255, 255, 255))
    
    # Path 3: Vertical Stem (with 80% opacity fill)
    stem_pts = []
    stem_pts.append(np.array([12.25, 14.5359]))
    stem_pts.append(np.array([12.25, 2.0]))
    # Curve from (12.25, 2) to (13.75, 6.243)
    stem_pts.extend(eval_cubic_bezier(np.array([12.25, 2.0]), np.array([12.25, 3.60747]), np.array([12.8119, 5.08371]), np.array([13.75, 6.243])))
    stem_pts.append(np.array([13.75, 18.0]))
    # Curve from (13.75, 18) to (12.25, 14.5359)
    stem_pts.extend(eval_cubic_bezier(np.array([13.75, 18.0]), np.array([13.75, 16.6339]), np.array([13.1733, 15.4024]), np.array([12.25, 14.5359])))
    
    stem_screen_pts = [map_pt(p[0], p[1]) for p in stem_pts]
    logo_draw.polygon(stem_screen_pts, fill=(255, 255, 255, 204))
    
    img = Image.alpha_composite(img, logo_layer)
    
    # If is_round (legacy round icon mask)
    if is_round:
        mask = Image.new("L", (canvas_size, canvas_size), 0)
        mask_draw = ImageDraw.Draw(mask)
        mask_draw.ellipse([0, 0, canvas_size, canvas_size], fill=255)
        rounded = Image.new("RGBA", (canvas_size, canvas_size), (0, 0, 0, 0))
        rounded.paste(img, (0, 0), mask=mask)
        img = rounded
        
    final_icon = img.resize((target_size, target_size), Image.Resampling.LANCZOS)
    return final_icon

def main():
    densities = {
        "mipmap-mdpi": 48,
        "mipmap-hdpi": 72,
        "mipmap-xhdpi": 96,
        "mipmap-xxhdpi": 144,
        "mipmap-xxxhdpi": 192,
    }
    
    res_dir = r"D:\Lunify\android\app\src\main\res"
    preview_path = r"C:\Users\soumo\.gemini\antigravity\brain\410f2ad7-bf42-46d9-8e2b-2e69544b8655\app_logo_preview2.png"
    
    # Generate 512x512 preview
    preview = create_lunify_icon(512, is_round=False)
    preview.save(preview_path, "PNG")
    print(f"Generated preview at {preview_path}")
    
    for folder, size in densities.items():
        folder_path = os.path.join(res_dir, folder)
        os.makedirs(folder_path, exist_ok=True)
        
        # Standard Icon
        icon = create_lunify_icon(size, is_round=False)
        icon.save(os.path.join(folder_path, "ic_launcher.webp"), "WEBP")
        
        # Round Icon
        round_icon = create_lunify_icon(size, is_round=True)
        round_icon.save(os.path.join(folder_path, "ic_launcher_round.webp"), "WEBP")
        
        print(f"Saved {folder}: {size}x{size}")

if __name__ == "__main__":
    main()
