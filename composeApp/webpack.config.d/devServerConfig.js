// composeApp/webpack.config.d/devServerConfig.js

config.devServer = {
    ...config.devServer,   // keep whatever Kotlin already set
    historyApiFallback: true
};
