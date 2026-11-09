import os
import glob
import xml.etree.ElementTree as ET

def convert_svg_to_vector(svg_path, out_path):
    tree = ET.parse(svg_path)
    root = tree.getroot()
    
    # Extract viewBox
    vb = root.attrib.get('viewBox', '0 0 24 24').split()
    vp_w = vb[2] if len(vb) == 4 else '24'
    vp_h = vb[3] if len(vb) == 4 else '24'
    
    paths = []
    
    def process_elem(elem):
        tag = elem.tag.split('}')[-1]
        fill = elem.attrib.get('fill', 'none')
        stroke = elem.attrib.get('stroke', 'currentColor')
        stroke_w = elem.attrib.get('stroke-width', '2')
        
        is_stroke = stroke != 'none'
        is_fill = fill != 'none' and fill != ''
        
        path_data = ''
        if tag == 'path':
            path_data = elem.attrib.get('d', '')
        elif tag == 'line':
            x1, y1 = elem.attrib.get('x1', '0'), elem.attrib.get('y1', '0')
            x2, y2 = elem.attrib.get('x2', '0'), elem.attrib.get('y2', '0')
            path_data = f'M {x1} {y1} L {x2} {y2}'
        elif tag == 'circle':
            cx = float(elem.attrib.get('cx', '0'))
            cy = float(elem.attrib.get('cy', '0'))
            r = float(elem.attrib.get('r', '0'))
            path_data = f'M {cx - r} {cy} A {r} {r} 0 1 0 {cx + r} {cy} A {r} {r} 0 1 0 {cx - r} {cy}'
        elif tag == 'rect':
            x = float(elem.attrib.get('x', '0'))
            y = float(elem.attrib.get('y', '0'))
            w = float(elem.attrib.get('width', '0'))
            h = float(elem.attrib.get('height', '0'))
            rx = float(elem.attrib.get('rx', '0'))
            ry = float(elem.attrib.get('ry', str(rx)))
            if rx == 0 and ry == 0:
                path_data = f'M {x} {y} h {w} v {h} h {-w} Z'
            else:
                path_data = f'M {x+rx} {y} h {w-2*rx} a {rx} {ry} 0 0 1 {rx} {ry} v {h-2*ry} a {rx} {ry} 0 0 1 {-rx} {ry} h {-(w-2*rx)} a {rx} {ry} 0 0 1 {-rx} {-ry} v {-(h-2*ry)} a {rx} {ry} 0 0 1 {rx} {-ry} Z'
        elif tag in ('polyline', 'polygon'):
            pts = elem.attrib.get('points', '').strip().replace(',', ' ').split()
            if len(pts) >= 2:
                path_data = f'M {pts[0]} {pts[1]} ' + ' '.join([f'L {pts[i]} {pts[i+1]}' for i in range(2, len(pts), 2)])
                if tag == 'polygon':
                    path_data += ' Z'
                    
        if path_data:
            p_xml = '    <path\n'
            p_xml += f'        android:pathData="{path_data}"\n'
            if is_fill and fill != 'currentColor':
                p_xml += f'        android:fillColor="{fill}"\n'
            elif is_fill:
                p_xml += '        android:fillColor="#FFFFFFFF"\n'
            else:
                p_xml += '        android:fillColor="#00000000"\n'
                
            if is_stroke:
                p_xml += '        android:strokeColor="#FFFFFFFF"\n'
                p_xml += f'        android:strokeWidth="{stroke_w}"\n'
                p_xml += '        android:strokeLineCap="round"\n'
                p_xml += '        android:strokeLineJoin="round"\n'
            p_xml += '    />'
            paths.append(p_xml)
            
        for child in elem:
            process_elem(child)

    for child in root:
        process_elem(child)
        
    xml_content = f'''<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="{vp_w}dp"
    android:height="{vp_h}dp"
    android:viewportWidth="{vp_w}"
    android:viewportHeight="{vp_h}">
''' + '\n'.join(paths) + '\n</vector>\n'

    with open(out_path, 'w', encoding='utf-8') as f:
        f.write(xml_content)

def main():
    out_dir = os.path.abspath('android/app/src/main/res/drawable')
    os.makedirs(out_dir, exist_ok=True)

    svgs = glob.glob('assets/icons/**/*.svg', recursive=True)
    count = 0
    for s in svgs:
        name = os.path.basename(s).replace('.svg', '').replace('-', '_')
        out_file = os.path.join(out_dir, f'ic_lucide_{name}.xml')
        convert_svg_to_vector(s, out_file)
        count += 1

    print(f'Converted {count} SVG icons to Android Vector Drawables in {out_dir}')

if __name__ == '__main__':
    main()
