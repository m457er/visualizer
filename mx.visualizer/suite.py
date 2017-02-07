suite = {
  "mxversion" : "5.67.1",
  "name" : "visualizer",

  "defaultLicense" : "GPLv2-CPE",

  "libraries" : {

  },

  "projects" : {

    "BatikSVGProxy": {
      "subDir" : "IdealGraphVisualizer",
      "sourceDirs" : ["src"],
      "checkstyle" : "Data",
      "javaCompliance" : "1.8",
    },

    "Bytecodes" : {
      "subDir" : "IdealGraphVisualizer",
      "sourceDirs" : ["src"],
      "dependecnies": [
        "Data",
        "Graph",
        "Util",
      ],
      "checkstyle" : "Data",
      "javaCompliance" : "1.8",
    },

    "Data" : {
      "subDir" : "IdealGraphVisualizer",
      "sourceDirs" : ["src"],
      "checkstyle" : "Data",
      "javaCompliance" : "1.8",
    },

    "ControlFlow" : {
      "subDir" : "IdealGraphVisualizer",
      "sourceDirs" : ["src"],
      "checkstyle" : "Data",
      "javaCompliance" : "1.8",
    },

    "Coordinator" : {
      "subDir" : "IdealGraphVisualizer",
      "sourceDirs" : ["src"],
      "checkstyle" : "Data",
      "javaCompliance" : "1.8",
    },

    "Difference" : {
      "subDir" : "IdealGraphVisualizer",
      "sourceDirs" : ["src"],
      "checkstyle" : "Data",
      "javaCompliance" : "1.8",
    },

    "Filter" : {
      "subDir" : "IdealGraphVisualizer",
      "sourceDirs" : ["src"],
      "checkstyle" : "Data",
      "javaCompliance" : "1.8",
    },

    "FilterWindow" : {
      "subDir" : "IdealGraphVisualizer",
      "sourceDirs" : ["src"],
      "checkstyle" : "Data",
      "javaCompliance" : "1.8",
    },

    "Graal" : {
      "subDir" : "IdealGraphVisualizer",
      "sourceDirs" : ["src"],
      "checkstyle" : "Data",
      "javaCompliance" : "1.8",
    },

    "Graph" : {
      "subDir" : "IdealGraphVisualizer",
      "sourceDirs" : ["src"],
      "checkstyle" : "Data",
      "javaCompliance" : "1.8",
    },

    "HierarchicalLayout" : {
      "subDir" : "IdealGraphVisualizer",
      "sourceDirs" : ["src"],
      "checkstyle" : "Data",
      "javaCompliance" : "1.8",
    },

    "Layout" : {
      "subDir" : "IdealGraphVisualizer",
      "sourceDirs" : ["src"],
      "checkstyle" : "Data",
      "javaCompliance" : "1.8",
    },

    "NetworkConnection" : {
      "subDir" : "IdealGraphVisualizer",
      "sourceDirs" : ["src"],
      "dependencies" : ["Data"],
      "checkstyle" : "Data",
      "javaCompliance" : "1.8=",
    },

    "SeletionCoordinator" : {
      "subDir" : "IdealGraphVisualizer",
      "sourceDirs" : ["src"],
      "checkstyle" : "Data",
      "javaCompliance" : "1.8",
    },

    "Settings" : {
      "subDir" : "IdealGraphVisualizer",
      "sourceDirs" : ["src"],
      "checkstyle" : "Data",
      "javaCompliance" : "1.8",
    },

    "Util" : {
      "subDir" : "IdealGraphVisualizer",
      "sourceDirs" : ["src"],
      "checkstyle" : "Data",
      "javaCompliance" : "1.8",
    },

    "View" : {
      "subDir" : "IdealGraphVisualizer",
      "sourceDirs" : ["src"],
      "checkstyle" : "Data",
      "javaCompliance" : "1.8",
    },

  },
}
