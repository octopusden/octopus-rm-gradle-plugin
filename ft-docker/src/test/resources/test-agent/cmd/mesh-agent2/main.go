package main

import (
	"git.domain.corp/system/go-commons.git/log"
	"git.domain.corp/system/test-agent.git/mesh"
)

var logger = log.NewLogger("mesh-main")

func main() {
    var m mesh.Mesh
    m.Start()
}
