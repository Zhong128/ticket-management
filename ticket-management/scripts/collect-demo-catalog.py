"""手动执行的公开演出元数据采集；只存链接与事实字段，不下载海报或复制介绍全文。"""

import concurrent.futures
import datetime as dt
import html
import json
import re
import urllib.parse
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
HEADERS = {"User-Agent": "Mozilla/5.0"}


def fetch(url):
    request = urllib.request.Request(url, headers=HEADERS)
    with urllib.request.urlopen(request, timeout=20) as response:
        return response.read().decode("utf-8")


def damai(item_id, category_override=None):
    url = f"https://detail.damai.cn/item.htm?id={item_id}"
    page = fetch(url)
    raw = re.search(r'<div id="staticDataDefault"[^>]*>(.*?)</div>', page, re.S)
    if not raw:
        raise ValueError(f"公开元数据不可用：{item_id}")
    data = json.loads(raw[1])
    item = data["itemBase"]
    venue = data["venue"]
    image = item["itemPic"]
    if image.count("https://") > 1:
        image = "https://" + image.split("https://")[-1]
    category = category_override or ("音乐剧" if "音乐剧" in item["itemName"] else "音乐节" if "ULTRA" in item["itemName"] else "音乐会" if "季默" in item["itemName"] else "演唱会")
    return {"name": item["itemName"], "sourceUrl": url, "sourceDates": item.get("showTime", "以官方为准"),
            "coverImage": image, "city": venue["venueCityName"].removesuffix("市"),
            "province": venue.get("venueProvinceName", ""), "venue": venue["venueName"], "category": category}


def collect():
    items = []
    source = "https://www.lydjy.cn/"
    for block in re.findall(r'<li class="con_li">(.*?)</li>', fetch(source), re.S):
        link = re.search(r'href="([^"]+)"', block)
        image = re.search(r'<img src="([^"]+)"', block)
        texts = re.findall(r'<p class="f18">(.*?)</p>', block)
        if not link or not image or len(texts) < 2 or "$" in texts[0]:
            continue
        name = html.unescape(texts[0])
        category = "儿童亲子" if "萌可" in name or "海底" in name else "舞蹈芭蕾" if "舞剧" in name else "脱口秀" if "脱口秀" in name else "歌剧话剧" if "话剧" in name or "麻花" in name else "音乐会"
        if name not in [item["name"] for item in items]:
            items.append({"name": name, "sourceUrl": urllib.parse.urljoin(source, link[1]), "sourceDates": texts[1],
                          "coverImage": image[1], "city": "临沂", "province": "山东省", "venue": "临沂大剧院", "category": category})

    ids = [1056937666584, 1066262874632, 743069303901, 1064051958557, 1061593027554,
           1070725013652, 1033785689625, 1050610012880, 983506021405, 1056358190015,
           1007466337082, 1032862506032, 1034515168538, 1002686485088]
    with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool:
        for item_id, future in zip(ids, [pool.submit(damai, item_id) for item_id in ids]):
            try:
                items.append(future.result())
            except Exception as error:
                print(f"跳过 {item_id}: {type(error).__name__}")

    # 2026-10-25核对大麦首页北京站公开分类推荐，仅收集事实字段和图片链接。
    homepage_groups = {
        "演唱会": [1090191217877, 1092383284574, 1091782629251, 1090182462464],
        "音乐节": [1089383688003],
        "音乐会": [1091883605136, 1091736156975, 1088164843352],
        "音乐剧": [1091895653399, 1086186896843, 1091607897808],
        "歌剧话剧": [1090754267670, 1088525651915, 1093559452942, 1084537866279],
        "体育": [1077238687549, 1091529721086, 1088814851105, 1094893609844,
                 1051407453406, 1052287524623, 1090766694766],
        "儿童亲子": [1086344643580, 1088298765593, 1086301199369,
                     1085629541334, 1088395655771, 1082710670898],
    }
    with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool:
        jobs = [(item_id, pool.submit(damai, item_id, category))
                for category, item_ids in homepage_groups.items() for item_id in item_ids]
        for item_id, future in jobs:
            try:
                items.append(future.result())
            except Exception as error:
                print(f"跳过 {item_id}: {type(error).__name__}")

    source = "https://www.chncpa.org/yshd/"
    for block in re.findall(r'<div class="item swiper-slide">(.*?)</h3>\s*<p>(.*?)</p>', fetch(source), re.S):
        link = re.search(r'href="([^"]+)"', block[0])
        image = re.search(r'<img src="([^"]+)"', block[0])
        title = re.search(r'<h3>(.*)', block[0], re.S)
        if link and image and title and any(word in title[1] for word in ("音乐会", "京剧", "评剧")):
            venue_parts = re.findall(r'<span>(.*?)</span>', block[1], re.S)
            venue_name = html.unescape(re.sub("<[^>]+>", "", venue_parts[-1])).strip()
            if venue_name == "音乐厅":
                venue_name = "国家大剧院音乐厅"
            category = "歌剧话剧" if "京剧" in title[1] and "音乐会" not in title[1] else "音乐会"
            items.append({"name": title[1].strip(), "sourceUrl": urllib.parse.urljoin(source, link[1]),
                          "sourceDates": re.sub("<[^>]+>", " ", block[1]).strip(),
                          "coverImage": urllib.parse.urljoin(source, image[1]), "city": "北京", "province": "北京市",
                          "venue": venue_name, "category": category})

    codes = {"北京": "BEIJING", "上海": "SHANGHAI", "广州": "GUANGZHOU", "深圳": "SHENZHEN", "临沂": "LINYI",
             "长沙": "CHANGSHA", "香港": "HONGKONG", "中国香港": "HONGKONG", "佛山": "FOSHAN", "南宁": "NANNING", "天津": "TIANJIN", "廊坊": "LANGFANG", "东京": "TOKYO", "东京都": "TOKYO"}
    destination = ROOT / "src/main/resources/demo/catalog-2026-10.json"
    existing = json.loads(destination.read_text(encoding="utf-8")) if destination.exists() else []
    existing_names = {item["name"] for item in existing}
    additional = [item for item in items if item["name"] not in existing_names]
    for index, item in enumerate(additional):
        item["cityCode"] = codes.get(item["city"], "DEMO_CITY_" + str(index))
        item["demoTime"] = (dt.datetime(2026, 11, 1, 19, 30) + dt.timedelta(days=index % 28)).isoformat()
        item["demoPrice"] = [180, 280, 380, 128, 480, 98][index % 6]
        item["demoState"] = "upcoming" if index % 7 == 1 else "sold-out" if index % 7 == 2 else "on-sale"
        item["verifiedAt"] = "2026-10-25"
    items = existing + additional
    destination.parent.mkdir(parents=True, exist_ok=True)
    destination.write_text(json.dumps(items, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"已保存 {len(items)} 条公开演出元数据（新增 {len(additional)} 条）：{destination}")


if __name__ == "__main__":
    collect()
