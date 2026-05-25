FROM gradle:8.7-jdk21
WORKDIR /workspace
COPY --chown=gradle:gradle . .
ENV ENV=dev
ENTRYPOINT ["gradle", "clean", "check", "cyclonedxBom", "allureReport", "--no-daemon"]
