import base64
import csv
import json
import os
import re
import typing
from datetime import date, datetime
from datetime import timedelta as td
from hashlib import sha256

import requests
from dateutil.parser import parse
from time import sleep

api = "https://www.speedrun.com/api/v1/"
use_milliseconds = True
use_hours = True
download_avatars = 10
pfp_dir = os.path.join("..", "data", "pfps")
country_dir = os.path.join("..", "data", "flags")
pfps = []
checked_pfps = []
checked_runs = []
countries = []
checked_countries = []


twitch_client = ""  # https://dev.twitch.tv/console/apps/
twitch_access = ""  # https://id.twitch.tv/oauth2/authorize?client_id=XXXXXXXXXXXXXXX&redirect_uri=http://localhost&response_type=token&scope=channel:read:subscriptions -- grab auth token from redirect url
if os.path.exists('twitch.txt'):
    with open('twitch.txt', 'r') as x:
        lines = [y.strip() for y in x.readlines()]
        twitch_client = lines[0]
        twitch_access = lines[1]
twitch_headers = {"client-id": twitch_client, "Authorization": "Bearer "+twitch_access}
twitch_sleep: datetime = None
twitch_id = re.compile(r"(\d{7,10})")

yt_client = ""
yt_access = ""
if os.path.exists('youtube.txt'):
    with open('youtube.txt', 'r') as x:
        lines = [y.strip() for y in x.readlines()]
        yt_client = lines[0]  # https://console.developers.google.com/apis/credentials
        yt_access = lines[1]  # https://accounts.google.com/o/oauth2/v2/auth?response_type=code&access_type=offline&client_id=XXXXXXXXXXXX&redirect_uri=urn:ietf:wg:oauth:2.0:oob&scope=https://www.googleapis.com/auth/youtube.readonly
yt_re = re.compile(r"([A-Za-z0-9_\-]{24})")
yt_params = {
    "access_token": yt_access,
    "key": yt_client,
    "part": "snippet"
}
yt_link = re.compile(r"youtu(?:\.be|be\.com)", re.IGNORECASE)
yt_id = re.compile(r"([A-Za-z0-9_\-]{11})")


def download_flag(country: str):
    if country not in checked_countries:
        checked_countries.append(country)
        dest = os.path.join(country_dir, country+".png")
        if os.path.exists(dest):
            countries.append(country)
        else:
            with requests.get(f"https://www.countryflags.io/{country}/flat/64.png", stream=True) as r:
                if r.status_code == 200:
                    with open(dest, 'wb') as f:
                        for c in r.iter_content(1024):
                            f.write(c)
                        countries.append(country)


class WebError(Exception):
    __slots__ = {"status_code", "payload"}

    def __init__(self, status_code: int, payload: dict):
        self.status_code = status_code
        self.payload = payload

    def __str__(self):
        stat = str(self.status_code)
        if 'message' in self.payload and self.payload['message']:
            stat += ': ' + self.payload['message']
        return stat


def get_id(user) -> str:
    if user['rel'] == 'user':
        return user['id']+'-'
    hsh = sha256()
    hsh.update(user['name'].encode('utf-8'))
    return base64.b32encode(hsh.digest()).decode('ascii')[:8].lower().replace('i', '8')+'_'


class Speedrun:
    def __init__(self, run):
        self.raw_data = run
        self.id = run['id']
        self.time = run['times']['primary_t']
        h = int(self.time/60/60)
        m = int(self.time/60 % 60)
        s = self.time % 60
        self.human_time = f"{h}:{m:0>2}:{s:0>6.3f}"
        if not use_milliseconds:
            self.human_time = self.human_time[:-4]
        if not use_hours:
            self.human_time = ':'.join(self.human_time.split(':')[1:])

        self.authors: typing.List[typing.Dict[str, typing.Any]] = run['players']['data']
        self.author_uuid = ''.join(map(get_id, sorted(self.authors, key=get_id)))
        self.author_names = [(x['names']['international'] if x['rel'] == 'user' else x['name']) for x in self.authors]

        self.region = None
        if run['region']['data']:
            self.region = run['region']['data']['name']
        self.platform = None
        if run['platform']['data']:
            self.platform = run['platform']['data']['name']
        self.emulated = run['system']['emulated']

        self.performed = None
        self._set_time()

        self.comment = None
        if 'comment' in run:
            self.comment = run['comment']

        # save flag
        for auth in self.authors:
            if 'location' in auth and auth['location'] is not None:
                country = auth['location']['country']['code']
                download_flag(country)

        if 'category_name' in run:
            self.category = run['category_name']
        else:
            self.category = run['category']

    def _set_time(self):
        if 'kn04ewol' in self.raw_data['values'] and self.raw_data['values']['kn04ewol'] == '4qyxop3l':
            # silly hack-fix to ignore "unverified" runs on sm64 leaderboards
            pass
        elif self.raw_data['date'] is not None:
            _date = list(map(int, self.raw_data['date'].split('-')))
            self.performed = date(year=_date[0], month=_date[1], day=_date[2])
        elif self.raw_data['submitted'] is not None:
            self.performed = parse(self.raw_data['submitted']).date()
        elif self.raw_data['status']['verify-date']:
            self.performed = parse(self.raw_data['status']['verify-date']).date()

    def __str__(self):
        return f"{self.human_time} by {' & '.join(self.author_names)} on {self.performed}"


def get_dest(uuid: str):
    return os.path.join(pfp_dir, uuid + ".png")


def get_avatar(run: Speedrun) -> bool:
    if len(run.authors) != 1:
        return False
    if not download_avatars:
        return False
    if 'weblink' not in run.authors[0]:
        return False
    auth = run.authors[0]
    name = auth['weblink'].split('/')[-1]
    dest = get_dest(run.author_uuid)
    if os.path.exists(dest):
        return True
    if download_file(f"https://www.speedrun.com/themes/user/{name}/image.png?version=", dest):
        return True
    if download_twitch(auth, dest):
        return True
    if download_youtube(auth, dest):
        return True
    return False


def query_twitch(url: str, params: dict = None) -> typing.List[dict]:
    global twitch_sleep
    if twitch_sleep is not None:
        delta = twitch_sleep - datetime.utcnow()
        sleep_sec = delta.total_seconds()
        if sleep_sec > 0:
            sleep(sleep_sec)
        twitch_sleep = None

    with requests.get(url, params=params, headers=twitch_headers) as r:
        if r.headers['Ratelimit-Remaining'] == 0:
            twitch_sleep = parse(r.headers['Ratelimit-Reset'])
        if r.status_code != 200:
            return []
        return r.json()['data']


def download_twitch(auth: dict, dest: str) -> bool:
    if not twitch_client or auth['twitch'] is None:
        return False
    twitch_name = auth['twitch']['uri'].strip('/').split('/')[-1]
    data = query_twitch("https://api.twitch.tv/helix/users", {"login": twitch_name})
    if not data:
        return False

    for t_user in data:
        if t_user['display_name'].lower() == twitch_name.lower():
            return t_user['thumbnail_url'] and download_file(t_user['thumbnail_url'], dest)


def download_youtube(auth: dict, dest: str) -> bool:
    if yt_client and auth['youtube'] is not None:
        match = yt_re.search(auth['youtube']['uri'])
        if not match:
            return False

        params = yt_params.copy()
        params['id'] = match.group(1)

        with requests.get(f"https://www.googleapis.com/youtube/v3/channels", params) as r:
            if r.status_code != 200:
                return False
            data = r.json()
            if data['pageInfo']['totalResults'] > 0:
                channel = data['items'][0]
                snip = channel['snippet']
                thumb = snip['thumbnails']
                return 'medium' in thumb and download_file(thumb['medium']['url'], dest)


def download_file(url: str, dest: str):
    with requests.get(url, stream=True) as r:
        if r.status_code != 200:
            return False
        with open(dest, 'wb') as f2:
            for c in r.iter_content(1024):
                f2.write(c)
        return True


def fetch(query: str, params: dict = None) -> typing.Union[typing.Dict, typing.List]:
    """
    Fetches a page from the SRC API
    :param query: page
    :param params: parameter of dictionaries to include
    :return:
    """
    if params is None:
        params = {}
    r = requests.get(api+query, params)
    data = json.loads(r.content)
    if r.status_code != 200:
        raise WebError(r.status_code, data)
    if 'data' in data and len(data['data']) == 0:
        raise WebError(9000, {"message": "No results"})
    return data['data']


def get_input(prompt: str, query: str, search_query: typing.Union[str, typing.Tuple, typing.List] = None, bulk: bool = False, values: int = None) -> typing.List[dict]:
    """
    Attempts to fetch a page from the SRC API based on user input
    :param prompt: what you are asking the user to input
    :param query: a string with a {} to query
    :param search_query: a string to use as a search parameter which creates a params dict of {search_query: user_input}
    :param bulk: whether to use bulk search or not
    :param values: maximum number of values to return
    :return:
    """
    print(prompt)
    if search_query is None or isinstance(search_query, str):
        search_query = (search_query,)
    while True:
        user_input = input("> ")
        exception = None
        for param in search_query:
            params = {"_bulk": int(bulk)}
            if values is not None:
                params["max"] = values
            if param is not None:
                params[param] = user_input
            try:
                return fetch(query.format(user_input), params)
            except WebError as e:
                exception = e
        print(f"Page not found ({exception}), please try again.")


def boolean_input(prompt: str, default: bool = None) -> bool:
    if default is not None:
        prompt += (" [Y/n]" if default else " [y/N]")
    print(prompt)
    while True:
        conf = input("> ")
        if conf == "" and default is not None:
            return default
        conf = conf.lower()
        if conf in ["y", "yes"]:
            return True
        if conf in ["n", "no"]:
            return False
        print("Could not process your input, please try again.")


def list_input(prompt: str, options: typing.List, default: int = None, mappings: typing.List = None):
    def rtrn(value: int):
        value -= 1
        if mappings is not None:
            return mappings[value]
        return value

    if len(options) != len(mappings):
        raise ValueError("Length of options and mappings don't match")

    if default:
        prompt += f" [Default: {default}]"
    print(prompt)

    options.insert(0, "[none match]")
    for i, item in enumerate(options):
        print(f"{i}. {item}")

    while True:
        choice = input("> ")
        if choice == "":
            return rtrn(default)
        try:
            choice = int(choice)
            if not (0 <= choice <= len(options)):
                raise IndexError("Option out of index")
            if choice == 0:
                return None
            return rtrn(choice)
        except (ValueError, IndexError):
            print("Could not process your input, please try again.")


# global variables annoy me
def main():
    os.makedirs(pfp_dir, exist_ok=True)
    os.makedirs(country_dir, exist_ok=True)

    # get game
    conf = False
    while not conf:
        games: typing.List[dict] = get_input("Please enter the game name or abbreviation.", "games", ("abbreviation", "name"), False, 5)
        if len(games) > 1:
            game_names = [x['names']['international'] for x in games]
            game = list_input("Please select the game from the list.", game_names, 1, games)
            conf = game is not None
        else:
            game = games[0]
            conf = boolean_input(f"Is {game['names']['international']} correct?", True)

    # get category
    game_id = game['id']
    categories = list(filter(lambda x: x['type'] == "per-game", fetch(f"games/{game_id}/categories")))
    if len(categories) == 0:
        print("No categories found.")
        return
    if len(categories) == 1:
        category = categories[0]
        print(f"Using the only category {category['name']}.")
    else:
        cat_names = [x['name'] for x in categories]
        category = list_input("Please select the desired category.", cat_names, 1, categories)
        if category is None:
            return

    global download_avatars
    print("How many users do you wish to display? This is used for downloading avatars. Enter 0 to skip.")
    count = input("> ")
    if count == "":
        count = "0"
    download_avatars = int(count)

    # # process data # #

    # fun pagination time !
    print("Fetching runs, please wait...")
    runs = []
    cats = map(lambda x: fetch(f"categories/{x}"), ["7dgrrxk4", "n2y55mko", "7kjpp4k3", "xk9gg6d0", "7kjrxx42", "7dggqwxd", "vdoq6z9k"])
    for c in cats:
        offset = 0
        while True:
            try:
                _max = 200
                _runs = fetch("runs", {"category": c['id'], "status": "verified", "max": _max, "offset": offset, "embed": "players,platform,region"})
                offset += _max
                for r in _runs:
                    r['category_name'] = c['name']
                runs += _runs
            except WebError as e:
                if e.status_code == 9000:
                    break
                else:
                    raise e

    if len(runs) == 0:
        print("No runs found.")
        return

    print("Generating player data")

    sruns = sorted(filter(lambda x: x.performed is not None, map(Speedrun, runs)), key=lambda x: x.performed)
    srunrawmap = {x.id: x for x in sruns}
    srunmap = {}
    runner_dict = {}
    for r in sruns:
        auths = r.authors.copy()
        for auth in auths:
            del auth['links']
            if auth['rel'] == 'user':
                del auth['role']
                del auth['signup']
        runner_dict[r.author_uuid] = auths

    # download cover
    c_url = game['assets']['cover-large']['uri']
    has_cover = False
    with requests.get(c_url, stream=True) as r:
        if r.status_code == 200:
            has_cover = True
            with open(os.path.join(pfp_dir, "_cover.png"), "wb") as f:
                for c in r.iter_content(1024):
                    f.write(c)

    print("Generating PB table")

    out = {x: None for x in ['date']+list(runner_dict.keys())}

    # file_re = re.compile(r"[^A-Za-z0-9-_ ]")
    # with open(file_re.sub("_", f"{game['names']['international']}-{category['name']}")+'.csv', 'w', newline='') as f:
    with open("runs.csv", 'w', newline='') as f:
        w = csv.DictWriter(f, fieldnames=out.keys(), quoting=csv.QUOTE_NONE)
        w.writeheader()

        _day = sruns[0].performed
        # _today = dt.today().date()
        _today = sruns[-1].performed
        _inc = td(days=1)
        check = sruns
        while _day <= _today:
            out['date'] = _day
            while check and check[0].performed <= _day:
                run = check.pop(0)
                if out[run.author_uuid] is not None and srunmap[out[run.author_uuid]]['time_t'] < run.time:
                    continue
                out[run.author_uuid] = run.id
                srunmap[run.id] = {
                    "time": run.human_time,
                    "time_t": run.time,
                    "comment": run.comment,
                    "region": run.region,
                    "platform": run.platform,
                    "emulated": run.emulated,
                    "category": run.category
                }
            w.writerow(out)
            _day += _inc
            if not check:
                break

    if download_avatars > 0:
        print("Saving avatars")

        with open("runs.csv", 'r', newline='') as f:
            z = csv.DictReader(f, fieldnames=list(out.keys()), quoting=csv.QUOTE_NONE)
            z.__next__()
            for row in z:
                runs = sorted(map(lambda x: srunrawmap[x], filter(lambda x: x != "", list(row.values())[1:])), key=lambda x: x.time)[:download_avatars]
                run: Speedrun
                for run in runs:
                    if run.author_uuid not in checked_pfps:
                        checked_pfps.append(run.author_uuid)
                        if get_avatar(run):
                            pfps.append(run.author_uuid)

                    # try to find runner pfp via VOD links
                    if run.id not in checked_runs and run.author_uuid not in pfps and run.raw_data['videos'] and run.raw_data['videos']['links']:
                        checked_runs.append(run.id)
                        for _l in run.raw_data['videos']['links']:
                            link: str = _l['uri']
                            if 'twitch.tv' in link.lower() and (m := twitch_id.search(link)):
                                v_id = m.group(1)
                                video = query_twitch("https://api.twitch.tv/helix/videos", {"id": v_id})
                                if video:
                                    video = video[0]
                                    user = query_twitch("https://api.twitch.tv/helix/users", {"id": video['user_id']})
                                    if user and user[0]['profile_image_url'] and download_file(user[0]['profile_image_url'], get_dest(run.author_uuid)):
                                        pfps.append(run.author_uuid)
                                        break

                            if yt_link.search(link) and (m := yt_id.search(link)):
                                params = yt_params.copy()
                                params['id'] = m.group(1)
                                with requests.get("https://www.googleapis.com/youtube/v3/videos", params) as r:
                                    if r.status_code == 200 and (j := r.json())['pageInfo']['totalResults'] > 0:
                                        channel_id = j['items'][0]['snippet']['channelId']
                                        fake_user = {"youtube": {"uri": "https://www.youtube.com/channel/"+channel_id}}
                                        if download_youtube(fake_user, get_dest(run.author_uuid)):
                                            pfps.append(run.author_uuid)
                                            break

    print("Generating metadata")

    with open("metadata.json", "w") as f:
        json.dump({
            "game": game['names']['international'],
            "category": category['name'],
            "runs": srunmap,
            "players": runner_dict,
            "pfps": pfps,
            "cover": has_cover,
            "flags": countries
        }, f)


if __name__ == "__main__":
    main()
