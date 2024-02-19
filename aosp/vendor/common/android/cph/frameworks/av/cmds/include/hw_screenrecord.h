
void hwSetStopRequestedFlag(bool flag);
void hwSetTimeDuration(uint32_t timeDuration);
void hwSetVideoBitRate(uint32_t bitRate);
void hwSetEncodeType(uint32_t encodeType);
void hwSetFilePrefix(std::string prefix);
void hwSetFilePath(std::string path);
void hwSetVideoResolution(uint32_t width, uint32_t height);

android::status_t hwRecordScreenWithAudio();