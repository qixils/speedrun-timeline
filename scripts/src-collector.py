import base64
import csv
import json
import os
import typing
from datetime import date
from datetime import timedelta as td
from hashlib import sha256

import requests
from dateutil.parser import parse

api = "https://www.speedrun.com/api/v1/"
use_milliseconds = True
use_hours = True
download_avatars = True
pfp_dir = os.path.join("..", "data", "pfps")
country_dir = os.path.join("..", "data", "flags")
pfps = []
checked_pfps = []
countries = []
checked_countries = []


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

        self.performed = None
        self._set_time()

        self.comment = None
        if 'comment' in run:
            self.comment = run['comment']

        # save flag
        for auth in self.authors:
            if 'location' in auth and auth['location'] is not None:
                country = auth['location']['country']['code']
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

        # this code really shouldn't be here but i'm lazy
        if self.author_uuid not in checked_pfps:
            checked_pfps.append(self.author_uuid)
            if self._get_avatar():
                pfps.append(self.author_uuid)

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

    def _get_avatar(self) -> bool:
        if self.performed is None:
            return False
        if len(self.authors) != 1:
            return False
        if not download_avatars:
            return False
        if 'weblink' not in self.authors[0]:
            return False
        name = self.authors[0]['weblink'].split('/')[-1]
        dest = os.path.join(pfp_dir, self.author_uuid + ".png")
        if os.path.exists(dest):
            return True
        with requests.get(f"https://www.speedrun.com/themes/user/{name}/image.png?version=", stream=True) as r:
            if r.status_code != 200:
                return False
            with open(dest, 'wb') as f:
                for c in r.iter_content(1024):
                    f.write(c)
                return True

    def __str__(self):
        return f"{self.human_time} by {' & '.join(self.author_names)} on {self.performed}"


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
        games: typing.List[dict] = get_input("Please enter the game name or abbreviation.", "games", ("abbreviation", "name"), True, 5)
        if len(games) > 1:
            game_names = [x['names']['international'] for x in games]
            game = list_input("Please select the game from the list.", game_names, 1, games)
            conf = game is not None
        else:
            game = games[0]
            conf = boolean_input(f"Is {game['names']['international']} correct?", True)

    # get category
    game_id = game['id']
    categories = fetch(f"games/{game_id}/categories")
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
    download_avatars = boolean_input("Would you like to download user avatars?", True)

    # # process data # #

    # fun pagination time !
    print("Fetching runs, please wait...")
    runs = []
    offset = 0
    while True:
        try:
            _max = 200
            _runs = fetch("runs", {"category": category['id'], "status": "verified", "max": _max, "offset": offset, "embed": "players"})
            offset += _max
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
    c_url = "https://www.speedrun.com/themes/{}/cover-256.png?version=".format(game['abbreviation'])
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
                    "comment": run.comment
                }
            w.writerow(out)
            _day += _inc
            if not check:
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
