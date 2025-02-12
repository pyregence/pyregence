const path = require("path");

module.exports = {
    mode: "production",
    context: path.resolve(__dirname),
    entry: path.resolve(__dirname, "target/public/cljs/app.js"),
    output: {
        path: path.resolve(__dirname, "target/public/cljs"),
        filename: "app.js",
        globalObject: "window",
    },
    resolve: {
        alias: {
            react: path.resolve("./node_modules/react"),
            "react-dom": path.resolve("./node_modules/react-dom"),
        },
    },
    optimization: {
        minimize: false,
        moduleIds: "named",
    },
};
