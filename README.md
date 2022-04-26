### IoTProfiler: Large-Scale Analysis on IoT Data Exposure through Mobile Companion Apps
IoTProfiler is an automated framework to recover IoT data, and detect their exposure by statically analyze IoT companion apps. In the core of the framework are a taxonmy of IoT private data and a machine learning model to infer such data from app snippets.

For more details, please refer to our manuscript: "Are You Spying on Me? Large-Scale Analysis on IoT Data Exposure through Companion Apps" in [here](https://github.com/iotprofiler/IoTProfiler-Public).

#### Prerequisites
In order to run the code, you need to install the following Java and Python dependencies:
- Install JDK 8+
- Install Python 3
  - python3 -m pip install fasttext
  - python3 -m pip install nltk
  - python3 -c "import nltk;nltk.download('wordnet')"
  - python3 -c "import nltk;nltk.download('omw-1.4')"

#### Setup
Please follow the below steps to run IoTProfiler.
- Clone this repository, and place it in: $dir/IoTProfiler-Public
- Change directory with: cd $dir/IoTProfiler-Public/apk-analysis/
- Compile the code with: ./compile.sh
- Run the analysis with: ./analyze.sh $apk_dir task_id
  - where $apk_dir is the path to a folder container android's APK files.

The raw output data, containing the (leaked) IoT data blocks, will be available under $dir/IoTProfiler-Public/output. For a better interpretation of the raw output, we are providing a few automation scripts and visualizer tools (will be available soon!)

## Disclaimer
Please use the tool at your own risk. 

## Contact
Anonymized (iotprofiler@gmail.com)
