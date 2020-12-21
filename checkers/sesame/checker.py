#!/usr/bin/env python3

import requests
import traceback
import random
import string

from bs4 import BeautifulSoup
from gornilo import CheckRequest, Verdict, Checker, PutRequest, GetRequest
from gornilo.models.verdict.verdict_codes import *

checker = Checker()


@checker.define_check
def check_service(request: CheckRequest) -> Verdict:
    flag = ''.join(random.choice(string.ascii_uppercase) for _ in range(31)) + "="
    put_verdict = put_flag(PutRequest(hostname = request.hostname, flag = flag, flag_id = None, vuln_id = 1))
    if put_verdict._code != OK:
        return put_verdict
    get_verdict = get_flag(GetRequest(hostname = request.hostname, flag = flag, flag_id = put_verdict._public_message, vuln_id = 1))

    return get_verdict


@checker.define_put(vuln_num=1, vuln_rate=1)
def put_flag(request: PutRequest) -> Verdict:
    url = "http://" + request.hostname + ":4280/"
    try:
        response = requests.post(url, data = { "secret": request.flag[:31] }, allow_redirects = False)
        key = response.headers['Location'][1:]
        print("Saved flag " + request.flag)
        return Verdict.OK(key)
    except:
        traceback.print_exc()
        return Verdict.MUMBLE("Couldn't get a meaningful response!")


@checker.define_get(vuln_num=1)
def get_flag(request: GetRequest) -> Verdict:
    url = "http://" + request.hostname + ":4280/" + request.flag_id.strip()
    try:
        response = requests.get(url)
        soup = BeautifulSoup(response.text, features="html.parser")
        secret = soup.find(id="secret").text
        if secret + "=" == request.flag:
            return Verdict.OK()
        if secret == '':
            print("Flag is missing for id = " + request.flag_id)
            return Verdict.CORRUPT("Flag is missing!")
        print("Flag value mismatch for id = " + request.flag_id + ". Got " + secret +
            ", wanted " + request.flag)
        return Verdict.CORRUPT("Flag value mismatch!")
    except:
        traceback.print_exc()
        return Verdict.MUMBLE("Couldn't get a meaningful response!")



if __name__ == '__main__':
    checker.run()
