import fasttext
import pickle
import tempfile
import json
import sys
import os
import re
from nltk.stem.wordnet import WordNetLemmatizer

POS_LABEL = '__label__pos'
iot_clf = fasttext.load_model(os.path.dirname(os.path.abspath(__file__)) + "/../output/ft_model.bin")

def hump2underline(hunp_str):
    p = re.compile(r'([a-z]|\d)([A-Z])')
    sub = re.sub(p, r'\1_\2', hunp_str).lower()
    return sub

def pre_processing_single(raw_doc):
    updated_list = []
    
    # remove non characteristics
    character_list = re.sub('[^a-zA-Z]+', ' ', str(raw_doc))

    for token in character_list.split(" "):
        # split Hump case. E.g., HelloWord --> hello world
        token = hump2underline(token)
        # handle under dash
        if "_" in token:
            temp_list = token.split("_")
            for item in temp_list:
                updated_list.append(item)
        elif len(token) <= 1:
            continue
        else:
            updated_list.append(token)
    
    lemmatizer = WordNetLemmatizer()
    tokens_lemmaed = [lemmatizer.lemmatize(word) for word in updated_list]
    
    return tokens_lemmaed

def is_iot_datablock(raw_doc):
    datablock_str = pre_processing_single(raw_doc)
    label_pair = iot_clf.predict(' '.join(datablock_str), k=2, threshold=0.0)
    if label_pair[0][0] == POS_LABEL and label_pair[1][0] >= 0.95:
        return '1'
    else:
        return '0'

if __name__ == '__main__':
    ipt = sys.argv[1]
    ret = ''
    datas = ipt.split('####')
    for data in datas:
        if len(data) < 1:
            continue
        ret = ret + is_iot_datablock(data) + '####'

    print (ret)
