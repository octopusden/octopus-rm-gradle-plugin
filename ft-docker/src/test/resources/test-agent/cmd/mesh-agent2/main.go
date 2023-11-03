package main

import (
	"git.domain.corp/system/golib.git/log"
	"git.domain.corp/system/test-agent.git/mesh"
)

var logger = log.NewLogger("mesh-main")

func main() {
    var m mesh.Mesh
    m.Start()
}
