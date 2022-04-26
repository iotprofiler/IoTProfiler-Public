import pickle
# from sklearn.model_selection import train_test_split
# from sklearn.utils import shuffle
from random import shuffle
from math import floor
from IPython import embed
import fasttext
import os

POS_LABEL = '__label__pos'
NEG_LABEL = '__label__neg'

pos_list = pickle.load(open("../data/iot_classes_0515.pkl", "rb"))
neg_list = pickle.load(open("../data/normal_classes_0501.pkl", "rb"))

print("pos: ", len(pos_list))
print("neg: ", len(neg_list))

f_train = open("../data/ft_balanced_train.txt", "a")

balanced_list = []
train_list = []
test_list = []

for item in pos_list:
    sentence = ""
    for subitem in item:
        sentence += " "
        sentence += subitem
    balanced_list.append(POS_LABEL + sentence + "\n")

shuffle(neg_list)
for item in neg_list[:len(pos_list)]:
    sentence = ""
    for subitem in item:
        sentence += " "
        sentence += subitem
    balanced_list.append(NEG_LABEL + sentence + "\n")

print("balanced list size: ", len(balanced_list))

shuffle(balanced_list)
train_idx = floor(len(balanced_list) * 0.7)
# valid_idx = floor(len(balanced_list) * 0.8)

# train_list
train_list = balanced_list[:train_idx]

# test_list
test_list = balanced_list[train_idx:]
# for item in neg_list[len(pos_list):]:
    # sentence = ""
    # for subitem in item:
        # sentence += " "
        # sentence += subitem
    # test_list.append(NEG_LABEL + sentence + "\n")

print("training set size", len(train_list))
for item in train_list:
    f_train.write(item)
f_train.close()

# for item in test_list:
    # f_test.write(item)

# statistics of testing set
pos_in_test = 0
print("testing set size: ", len(test_list))
for item in test_list:
    if item[:12] == POS_LABEL:
        pos_in_test += 1
print(pos_in_test, " pos in testing set")

# training
# model = fasttext.train_supervised(input='../data/ft_train.txt', autotuneValidationFile='../data/ft_test.txt')
model = fasttext.train_supervised(input='../data/ft_balanced_train.txt', lr=0.01, lrUpdateRate=50, epoch=2000)

tp = 0
tn = 0
fp = 0
fn = 0
for item in test_list:
    label_pair = model.predict(item[13:].rstrip().replace('\n', ' '), k=2, threshold=0.0)
    # label = label_pair[0][0]

    if label_pair[0][0] == POS_LABEL and label_pair[1][0] >= 0.95:
        label = POS_LABEL 
    # elif label_pair[0][1] == POS_LABEL and label_pair[1][1] >= 0.5: 
    #     label = POS_LABEL
    else:
        label = NEG_LABEL 

    # label = model.predict(item[13:].rstrip(), k=1)[0][0]

    if label == NEG_LABEL:
        if item[:12] == NEG_LABEL:
            tn += 1
        elif item[:12] == POS_LABEL:
            fn += 1
        else:
            print("exception0")
    elif label == POS_LABEL:
        if item[:12] == POS_LABEL:
            tp += 1
        elif item[:12] == NEG_LABEL:
            fp += 1
        else:
            print("exception2")
    else:
        print("exception3")

print("tp: %d, tn: %d, fp: %d, fn: %d: " % (tp, tn, fp, fn))
print("precision: %f" % (float(tp) / (tp+fp)))
print("recall: %f" % (float(tp) / (tp+fn)))
print("f1: %f" % (float(2 * tp) / (2 * tp + fp + fn)))

#os.remove('../data/ft_balanced_train.txt')

# save the model
try:
    model.save_model('../output/ft_model.bin')
except:
    embed()

